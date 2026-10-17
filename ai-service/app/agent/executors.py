"""工具执行器：智能体与旧路由共用的唯一实现

⚠ 所有"把关"逻辑都放在这里，不放提示词、也不放图结构——因为它们是量化实验实测出来的：
  · kb_search     → 检索为空/低于 KB_SCORE_THRESHOLD 时返回 status=empty，由 generate 走确定性拒答
  · product_search → 商详指代（productId 有、keyword 无）→ 品类 LIKE（成类才采信）→
                     语义强命中（REC_MIN_SCORE=0.47，不是搜索框的 0.35）→ 域外 refused → 热销兜底
  实验五的 60%→0% 泄漏率结论就挂在这条链上，重构时绝不能把它绕过。

Observation 是"工具回了什么"的统一形态：payload 进生成节点的上下文，
items 供前端渲染卡片，status/kind 决定生成节点走哪条出口（含确定性拒答）。
"""
import json
import logging
from dataclasses import dataclass, field, replace

from app.config import settings
from app.prompts import format_context
from app.tools import java_api

logger = logging.getLogger("agsell.ai.executors")

# 未登录时给用户的引导话术（业务工具需要 userId 才能取到"他自己的"数据）
NEED_LOGIN_HINT = "暂时无法确认您的登录身份，请到小程序登录后咨询，或拨打客服电话 "


@dataclass
class ToolContext:
    """执行器依赖注入点：全部可被测试替身替换。

    注意字段必须带类型注解——只写 `retriever=None` 的话 dataclass 不会把它当字段
    （构造时会得到 unexpected keyword argument）。
    """

    retriever: object = None
    product_store: object = None
    personalizer: object = None
    java_api_module: object = None

    @property
    def api(self):
        return self.java_api_module or java_api


@dataclass
class Observation:
    tool: str
    kind: str = ""            # kb|recommend|product_qa|order|logistics|after_sales|human
    status: str = "ok"        # ok|empty|refused|error|need_login
    payload: str = ""         # 给生成节点看的资料（status!=ok 时一律为空，避免内部细节外泄）
    items: list = field(default_factory=list)   # 前端卡片数据
    args: dict = field(default_factory=dict)    # 模型给本工具的入参（审计与测评要看"它到底问了什么"）
    # 只回灌给规划器的内部提示（如"该能力未开放"）。故意不进 as_dict：
    # 作答上下文里混进系统指令会让模型照着说，也会污染多轮状态。
    hint: str = ""

    def as_dict(self) -> dict:
        return {"tool": self.tool, "kind": self.kind, "status": self.status,
                "payload": self.payload, "items": self.items, "args": self.args}

    @property
    def is_empty(self) -> bool:
        return self.status in ("empty", "refused", "need_login", "error") or not self.payload


# ---------- 商详页上下文（"这个"的指代对象）----------
def load_product_context(state: dict, api_module) -> tuple[str, dict | None]:
    """按 state.productId 取该在售商品的事实。

    没有 productId、商品已下架、或 Java 侧不可用时一律返回 ("", None)——
    聊天链路绝不因为"取上下文失败"而给用户报错，退回常规问答即可。
    state 里已有本轮事实时直接复用：agent 节点会预取一次，各执行器不再重复回调 Java。
    """
    if state.get("product_context") or state.get("product_item"):
        return state.get("product_context") or "", state.get("product_item")
    pid = state.get("productId")
    if not pid:
        return "", None
    try:
        item = api_module.fetch_product(int(pid))
    except Exception as e:
        logger.warning("商品上下文获取失败（忽略，退回常规问答）：productId=%s, %s", pid, e)
        return "", None
    if not item:
        return "", None
    return java_api.format_product_facts(item), item


def _need_login(ctx: ToolContext, state: dict, tool: str) -> Observation | None:
    """业务工具的登录闸门。

    tool 用真实工具名而不是"guard"：这条观察会回灌给规划器，名字对不上工具表
    会让模型以为出现了一个新东西，反而诱发乱调。
    """
    if state.get("userId"):
        return None
    return Observation(tool, status="need_login",
                       payload=NEED_LOGIN_HINT + settings.SERVICE_PHONE + " 处理。")


def _failed(tool: str, kind: str, err: Exception | str, what: str = "") -> Observation:
    """取数失败的统一形态：细节只进日志，payload 一律留空。

    生成节点按 status!='ok' 判定"本轮没取到资料"，走友好降级——
    异常文本里有内网地址、集合名、HTTP 状态码，进上下文就有概率被复述给用户。
    """
    logger.warning("%s 取数失败%s：%s: %s", tool, f"（{what}）" if what else "", type(err).__name__, err)
    return Observation(tool, kind=kind, status="error", payload="")


# ---------- 各工具 ----------
def exec_kb_search(ctx: ToolContext, state: dict, args: dict) -> Observation:
    """知识库检索。低于阈值/零命中 → status=empty，交给生成节点的确定性拒答；
    检索器故障 → status=error（细节只进日志，见 _failed）。"""
    question = (args.get("question") or "").strip() or _last_question(state)
    if ctx.retriever is None:
        return _failed("kb_search", "kb", "retriever 未装配")
    try:
        hits = ctx.retriever.retrieve(question)
    except Exception as e:
        return _failed("kb_search", "kb", e)
    if not hits:
        return Observation("kb_search", kind="kb", status="empty", payload="")
    return Observation("kb_search", kind="kb", payload=format_context(hits))


def exec_product_search(ctx: ToolContext, state: dict, args: dict) -> Observation:
    """导购五级：商详指代 → 品类 LIKE → 语义召回（去重合并）→ 域外拒答 → 热销兜底。

    顺序即证据强度，每一级都有实测依据：
    ⓪ 正在浏览某件商品且模型没点出品类词 → 问的就是它。指代句的向量分与真品类问句重叠，
      阈值救不了（见下方数字），判据必须是结构性的。
    ① 品类词 LIKE：名字里真有这个词是最硬的证据，排最前；
    ② 语义强命中（闸门 REC_MIN_SCORE=0.47，不是搜索框的 0.35）覆盖"适合送礼的柑橘"这类
      说不成品类的描述性需求，并把 LIKE 的单薄结果补齐到卡片上限——只给 LIKE 命中会"准而薄"
      （实测"有什么水果推荐"LIKE 只剩水果玉米/水果胡萝卜两条）。两条通道去重合并。
    ③ 两条都没成 → 明确域外才拒答（inDomain 缺省时不拒，与改造前一致）；否则热销兜底。
    """
    api = ctx.api
    question = (args.get("query") or "").strip() or _last_question(state)
    keyword = (args.get("keyword") or "").strip() or None
    max_price = args.get("maxPrice")
    max_price = float(max_price) if isinstance(max_price, (int, float)) else None
    in_domain = args.get("inDomain") if isinstance(args.get("inDomain"), bool) else None

    facts, item = load_product_context(state, api)

    def _rank(products: list, sem: dict) -> list:
        if ctx.personalizer is not None and products:
            return ctx.personalizer.rerank(state.get("userId"), products, sem)
        return products

    def _ok(products: list, note: str = "", kind: str = "recommend", sem: dict | None = None,
            lead: list | None = None) -> Observation:
        """统一出口：证据序 → 个性化重排 → 截断成卡片上限，文本与卡片同源。

        payload 与 items 必须是同一份列表：否则 LLM 讲的是前两款、用户点的卡片是另外几款。
        lead（品类词 LIKE 命中）在重排**之后**仍然置顶：个性化分会把一整批无关候选排到前面，
        但用户点名要的品类命中不该被挤出首屏——实测"有什么水果推荐"重排后 LIKE 命中的
        水果玉米/水果胡萝卜直接不见了，就是被 rerank 挪走的。
        """
        sem = sem or {}
        lead_ids = {p.get("id") for p in (lead or [])}
        rest = _rank([p for p in products if p.get("id") not in lead_ids], sem)
        products = _rank(list(lead or []), sem) + rest
        products = products[:settings.REC_CARD_LIMIT]
        payload = api.format_payload(products)
        return Observation("product_search", kind=kind,
                           payload=(note + "\n" + payload) if note else payload, items=products)

    # ⓪ 商详页指代：实测「这个能放多久？怎么保存」top 分 0.518、8 条过闸门（首条是金针菇），
    #    「它新鲜吗」0.526 / 8 条，而真品类问句「哪种茶叶比较好」也只有 0.558 / 8 条——
    #    两类分数完全重叠，靠调阈值分不开，只能用"有 productId 且无 keyword"这个结构判据。
    if item and not keyword:
        return Observation("product_search", kind="product_qa", payload=facts, items=[item],
                           hint="用户正在浏览这件商品，问题按指代它来处理；"
                                "若他其实想问别的品类，请在下次调用时给出 keyword")

    # ① 品类词 LIKE：命中的排最前面（名字里真有这个词 = 最硬的证据），但不再独占出口。
    #    实测"有什么水果推荐"LIKE 只命中「水果玉米 / 水果胡萝卜」两条，只给这两张卡
    #    比给一堆无关水果更准却更薄——所以 LIKE 之后仍要用语义召回补齐到卡片上限。
    like_hits: list = []
    if keyword:
        try:
            like_hits = api.search_products(keyword=keyword, max_price=max_price) or []
        except java_api.JavaApiError as e:
            return _failed("product_search", "", e, what=f"LIKE keyword={keyword}")

    # ② 语义召回：对话侧独立闸门 REC_MIN_SCORE（0.47）。搜索框的 0.35 有全文检索可退，
    #    导购分支没有退路，阈值一松就把"手机壳"召回成"带壳毛豆"（实验五实测 60% 泄漏）。
    sem_products: list = []
    sem_by_id: dict = {}
    if ctx.product_store is not None:
        try:
            hits = [h for h in ctx.product_store.search(question) if h["score"] >= settings.REC_MIN_SCORE]
            recalled = [h["id"] for h in hits]
            sem_by_id = {h["id"]: h["score"] for h in hits}
            if recalled:
                sem_products = _reorder_by_recall(
                    api.search_products(ids=recalled, max_price=max_price), recalled)
        except Exception as e:
            logger.warning("商品向量召回不可用，降级关键词/热销路径：%s", e)

    seen_ids = set()
    merged = []
    for p in list(like_hits) + list(sem_products):
        pid = p.get("id")
        if pid in seen_ids:
            continue
        seen_ids.add(pid)
        merged.append(p)
    if merged:
        # LIKE 命中优先于模型的域判断：宁可少拒一次，也不能把在卖的商品拒掉
        return _ok(merged, sem=sem_by_id, lead=like_hits)

    # ③ 两条通道都空手时才继续往下走（拒答或热销兜底）

    # ④ 域外拒答：不经 LLM、不降级热销榜
    if in_domain is False:
        return Observation("product_search", status="refused", payload="")

    # ⑤ 热销兜底
    try:
        products = api.search_products(max_price=max_price)
    except java_api.JavaApiError as e:
        return _failed("product_search", "", e, what="热销榜")
    if not products:
        return Observation("product_search", kind="recommend", payload="未找到符合偏好的在售商品")
    if keyword:
        # 品类词 LIKE 无命中（用户说"水果"，商品名是"赣南脐橙"）→ 降级热销榜，
        # 并在上下文中注明原因，由生成节点如实引导
        note = f"（未找到与「{keyword}」直接匹配的商品，以下为热销候选，请如实向用户说明并引导其换个说法）"
        return _ok(products, note=note)
    return _ok(products)


def exec_order_query(ctx: ToolContext, state: dict, args: dict) -> Observation:
    blocked = _need_login(ctx, state, "order_query")
    if blocked:
        return blocked
    api = ctx.api
    try:
        order_no = (args.get("orderNo") or "").strip() or state.get("order_no")
        if order_no:
            data = api.query_order_detail(state["userId"], order_no)
        else:
            data = api.query_orders(state["userId"])
        return Observation("order_query", kind="order", payload=api.format_payload(data))
    except java_api.JavaApiError as e:
        return _failed("order_query", "order", e)


def exec_logistics_query(ctx: ToolContext, state: dict, args: dict) -> Observation:
    blocked = _need_login(ctx, state, "logistics_query")
    if blocked:
        return blocked
    api = ctx.api
    try:
        order_no = (args.get("orderNo") or "").strip() or state.get("order_no")
        if not order_no:
            orders = api.query_orders(state["userId"])
            if not orders:
                return Observation("logistics_query", kind="logistics", status="empty", payload="该用户暂无订单")
            order_no = orders[0]["orderNo"]
        data = api.query_logistics(state["userId"], order_no)
        return Observation("logistics_query", kind="logistics", payload=api.format_payload(data))
    except java_api.JavaApiError as e:
        return _failed("logistics_query", "logistics", e)


def exec_after_sales_query(ctx: ToolContext, state: dict, args: dict) -> Observation:
    blocked = _need_login(ctx, state, "after_sales_query")
    if blocked:
        return blocked
    api = ctx.api
    try:
        data = api.query_after_sales(state["userId"])
        return Observation("after_sales_query", kind="after_sales", payload=api.format_payload(data))
    except java_api.JavaApiError as e:
        return _failed("after_sales_query", "after_sales", e)


def exec_escalate_human(ctx: ToolContext, state: dict, args: dict) -> Observation:
    reason = (args.get("reason") or "").strip()
    logger.info("智能体判定转人工：%s", reason or "（未给原因）")
    return Observation("escalate_human", kind="human", payload="")


EXECUTORS = {
    "kb_search": exec_kb_search,
    "product_search": exec_product_search,
    "order_query": exec_order_query,
    "logistics_query": exec_logistics_query,
    "after_sales_query": exec_after_sales_query,
    "escalate_human": exec_escalate_human,
}

# 工具 → 资料类别（未知异常时用于保留 kind，供生成节点判定该给哪类友好出口）
_TOOL_KIND = {
    "kb_search": "kb",
    "product_search": "recommend",
    "order_query": "order",
    "logistics_query": "logistics",
    "after_sales_query": "after_sales",
    "escalate_human": "human",
}


def run_tool(ctx: ToolContext, state: dict, name: str, args: dict | None = None) -> Observation:
    """执行一个工具。未知工具/执行异常都归一成 error 观察，绝不让异常冒到用户面前。"""
    args = args or {}
    fn = EXECUTORS.get(name)
    if fn is None:
        logger.warning("模型请求了未注册的工具：%s", name)
        return Observation(name or "unknown", status="error", args=args,
                           hint="该能力未开放，请改用已有工具，或据此向用户说明无法处理")
    try:
        obs = fn(ctx, state, args)
    except Exception as e:  # noqa: BLE001 工具执行失败不能拖垮整条对话链路
        logger.exception("工具执行异常：%s", name)
        return _failed(name, _TOOL_KIND.get(name, ""), e)
    # 记下模型给的入参：测评要看"它到底问了什么"，排障也要这条
    return obs if obs.args else replace(obs, args=args)


def _last_question(state: dict) -> str:
    msgs = state.get("messages") or []
    last = msgs[-1] if msgs else None
    return (getattr(last, "content", "") or "") if last is not None else ""


def _reorder_by_recall(products: list[dict], recalled_ids: list[int]) -> list[dict]:
    """按向量召回顺序重排 Java 返回的商品列表（Java 侧默认销量降序，这里恢复语义相关度序）"""
    rank = {pid: i for i, pid in enumerate(recalled_ids)}
    return sorted(products, key=lambda p: rank.get(p.get("id"), len(recalled_ids)))


def observations_as_prompt(obs_list: list[Observation]) -> str:
    """把多次工具调用合并成生成节点看的上下文（多意图一轮取证靠这里拼回去）。"""
    return json.dumps([o.as_dict() for o in obs_list if o.payload or o.items], ensure_ascii=False)
