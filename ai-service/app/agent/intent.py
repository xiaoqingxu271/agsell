"""意图识别 + 导购偏好抽取：高置信规则前置 + LLM 一次调用 + 关键词兜底

三级路由（成本从低到高，命中即返回，最大化省掉 LLM 往返）：
1. _hard_rule：人工强诉求 / 纯寒暄 —— 高置信、零成本、毫秒级，直接命中；
2. LLM：**一次调用同时输出意图与导购偏好**（keyword / maxPrice / inDomain）。
   此前意图分类与偏好抽取是两次串行 LLM，实测导购分支首 token 19 240 ms（全系统最慢），
   合并后该分支从 3 次调用降到 2 次；量化依据见 doc/量化实验报告.md 实验四；
3. _keyword_fallback：LLM 不可用时的关键词规则兜底，保证路由不中断。

意图标签：faq / order / logistics / after_sales / smalltalk / human / recommend
"""
import json
import re
from dataclasses import dataclass

from langchain_core.messages import SystemMessage
from langchain_core.messages.base import BaseMessage

INTENT_LABELS = ("faq", "order", "logistics", "after_sales", "smalltalk", "human", "recommend")

_INTENT_PROMPT = """你是电商客服的意图识别器。只输出一个 JSON 对象，不要输出任何其他内容。

根据用户消息判断意图，可选标签：
- faq：询问平台规则/商品/运费/售后政策等常识问题（如"运费多少""怎么退货""支持七天无理由吗"）
- order：查询订单状态/订单列表/购买记录（如"我的订单""那单蜂蜜是什么状态""订单到哪了"）
- logistics：查询物流/发货/配送进度（如"发货没有""快递到哪了""什么时候能送到"）
- after_sales：查询退款/售后/换货/理赔的处理进度（如"退款到哪了""换货处理好了吗""售后审核通过没"）
- smalltalk：寒暄、打招呼、闲聊、礼貌结束语，且不含任何业务诉求（如"你好""谢谢""辛苦啦""好的知道了""你叫什么名字"）
- human：需要人工处理（投诉、举报、情绪激烈、要求赔偿、要求转人工/找真人/找领导、涉及账号隐私或复杂纠纷）
- recommend：想让我推荐/挑选商品（如"有什么水果推荐""帮我挑个礼物""买点什么好""50元以内有什么""哪种茶叶比较好""销量最高的是哪些"）

易混判断示例（务必参照）：
- "辛苦啦""麻烦你了""好的，知道了""今天心情不错""你是机器人吗""你叫什么名字" → smalltalk（纯寒暄）
- 询问助手本身是什么（是不是真人/机器人/AI）→ smalltalk，不是 human；
  但明确要求人工介入（要真人客服、要转人工、要找负责人）→ human，两者别混
- "谢谢你，我的退款到哪了" → after_sales（虽含"谢谢"，但核心是查退款，按业务意图判，不是 smalltalk）
- "你好，我想查下订单" → order（寒暄后带业务诉求，按业务意图判，不是 smalltalk）
- "哪种茶叶比较好""销量最高的商品是哪些""有没有便宜又好吃的水果""第一次买不知道买什么好" → recommend
- "叫你们领导来""我要找真人客服""你们要负责任" → human
- "我下的那单蜂蜜是什么状态""上周那笔订单什么情况" → order（问具体订单状态，不是 faq）
- "我买的米怎么还显示未发货""什么时候能送到""包裹揽收了吗" → logistics（问发货/配送进度，不是 order）
- "换货的那单处理好了吗""帮我看看退款审核到哪一步""赔偿金额什么时候给我" → after_sales（问售后/退款进度，不是 logistics/faq）
- "怎么退货""支持七天无理由退货吗" → faq（咨询退换货"政策/规则"，而非查询某笔售后单进度）
- 围绕派送环节要具体信息或提派送要求（要派送员联系方式、改派送时间、问包裹经过哪个城市）→ logistics，
  不要因为句子里有"能不能/给我"就判成 human，也不要因为问的是细节就判成 faq

当意图为 recommend 时，再从同一句话里抽取导购条件（其他意图一律填 null，不要臆测）：
- keyword：想买的商品品类或名称（如"水果""茶叶""大米""坚果"），说不清就 null
- maxPrice：价格上限数字（"50元以内"→ 50；没有就 null）
- inDomain：用户想买的东西**是否属于农产品/食品/特产这类本平台的经营范围**（水果、粮油、茶叶、零食、
  生鲜、滋补特产等 → true；电影、手机、衣服、健身房、书、化妆品、跑步鞋等本平台不卖的品类 → false；
  "有什么好物""第一次买不知道买什么"这类没有具体品类的泛推荐 → true）

输出格式：{"intent": "<标签>", "order_no": "<消息中提到的订单号，没有则为 null>", "keyword": <品类词或null>, "maxPrice": <数字或null>, "inDomain": <true 或 false>}
只输出 JSON。"""


@dataclass
class IntentDecision:
    """一次 LLM 调用的全部产物：路由标签 + 订单号 + 导购偏好。

    in_domain 为 None 表示"没拿到大模型判定"（被规则短路，或 LLM 故障走了兜底）：
    导购节点据此退回原有的三级降级路径——缺判定时可以兜底，但不能据此拒答。
    """

    intent: str
    order_no: str | None = None
    keyword: str | None = None
    max_price: float | None = None
    in_domain: bool | None = None
    from_llm: bool = False


# LLM 不可用时的关键词兜底规则：按顺序首个命中即返回，human/售后优先级最高。
# 注意 after_sales 排在 logistics 之前，避免"退款到哪一步"被物流词"到哪"截胡。
_KEYWORD_RULES = [
    (("human",), ("投诉", "举报", "曝光", "315", "人工", "真人", "领导", "监管", "爆料", "欺诈", "售假", "说法", "负责")),
    (("after_sales",), ("退款", "售后", "退货", "退换", "换货", "仅退款", "坏果", "理赔", "赔偿", "补偿金")),
    (("logistics",), ("物流", "发货", "快递", "配送", "送到", "送达", "派送", "到哪", "运单", "签收", "包裹", "揽收", "网点", "到货")),
    (("order",), ("订单", "下单", "买过", "购买记录", "待发货", "待收货", "确认收货")),
    (("recommend",), ("推荐", "帮我挑", "帮我选", "选一款", "挑个", "买点什么", "买什么", "哪种", "哪个好", "好物", "特价", "热销", "销量", "便宜", "排行")),
    (("smalltalk",), ("你好", "您好", "早上好", "在吗", "谢谢", "再见", "hi", "hello", "辛苦", "麻烦你", "好的", "知道了")),
]

# ---------- 高置信规则前置（先于 LLM，零成本毫秒级）----------

# 人工强诉求词：只要出现即转人工。
# 不含"赔偿"——"赔偿金额什么时候给我"属于售后进度查询，应由 after_sales 处理。
_HARD_HUMAN_WORDS = (
    "投诉", "举报", "曝光", "315", "人工", "真人", "领导", "监管", "爆料", "欺诈", "售假", "说法", "负责",
)

# 身份疑问句式：问"你是不是真人/机器人/AI"属于 smalltalk，不是要求转人工。
# 实验二实测：hold-out 里"你是真人还是智能助手"被"真人"一词硬判 human，而纯大模型判对了 smalltalk，
# 这是规则前置层自己的假阳性。真实诉求（"我要找真人客服""给我转人工"）不受本表影响。
_IDENTITY_PATTERNS = (
    "你是真人", "你是机器人", "你是不是机器人", "你是不是真人", "你是人工智能", "你是智能助手",
    "你是ai", "你是人工智能助手", "是真人还是", "是机器人还是",
)

# 与"你是"共现即视为在问助手身份的标记词
_IDENTITY_MARKERS = ("真人", "机器人", "人工", "智能", "ai", "人工智能")

# 强诉求词：出现时优先转人工，哪怕句子里同时含"你是…机器人"（如"你是机器人吗？我要投诉"）
_STRONG_HUMAN_WORDS = ("投诉", "举报", "曝光", "315", "欺诈", "售假", "监管", "领导", "真人客服")

# 纯寒暄词：需同时满足"短句 + 不含业务词"才前置 smalltalk，避免"谢谢，退款到哪了"被误判。
_SMALLTALK_WORDS = (
    "你好", "您好", "早上好", "上午好", "中午好", "下午好", "晚上好", "早安", "晚安", "在吗", "在不在",
    "再见", "拜拜", "谢谢", "多谢", "辛苦", "麻烦你", "好的", "知道了", "明白了", "收到", "心情",
    "机器人", "叫什么", "名字", "hi", "hello", "hey",
)

# 业务否决词：寒暄消息中一旦夹带这些词，说明有真实业务诉求，交给 LLM 判业务意图。
_BUSINESS_BLOCK_WORDS = (
    "订单", "下单", "买过", "购买", "付款", "支付", "退款", "退货", "退换", "换货", "售后", "理赔", "赔偿",
    "物流", "发货", "快递", "配送", "送到", "运单", "签收", "收货", "包裹", "商品", "水果", "价格", "运费",
    "优惠", "券", "发票", "地址", "会员", "秒杀", "溯源", "搜索", "推荐", "挑", "选", "买什么", "客服", "钱",
)

# 纯寒暄前置的句长上限（汉字/字符数）：超长句更可能夹带业务描述
_SMALLTALK_MAX_LEN = 12


def _is_identity_question(question: str) -> bool:
    """是否在问助手身份——这类句子不能贴 human 标签（实验二测出的规则假阳性）。"""
    q = question.lower()
    if any(p in q for p in _IDENTITY_PATTERNS):
        return True
    # 覆盖"你是真人吗""你是机器人吗""你是AI吗""你是人工客服吗"等变体：
    # 必须同时出现"你是"与身份标记词，避免把"叫你们人工来"这类真实诉求误伤
    return "你是" in q and any(m in q for m in _IDENTITY_MARKERS)


def _hard_rule(question: str) -> str | None:
    """高置信规则前置。命中返回意图标签，否则返回 None（交给 LLM）。"""
    q = question.lower()
    # 真实强诉求优先：带投诉/举报等词的长句即便含"你是…机器人"也必须先转人工
    strong_human = any(w in q for w in _STRONG_HUMAN_WORDS)
    if _is_identity_question(question) and not strong_human:
        # 问助手身份（"你是机器人吗""你是真人还是智能助手"）是纯寒暄，不是要求转人工。
        # 实验二实测：这类句子曾被"真人/机器人"硬判 human——规则层自己的假阳性；
        # 原先还要花一次 LLM 才判对，现在直接前置为 smalltalk，省钱又把结果钉死。
        return "smalltalk"
    if any(w in q for w in _HARD_HUMAN_WORDS):
        return "human"
    if len(question.strip()) <= _SMALLTALK_MAX_LEN and any(w in q for w in _SMALLTALK_WORDS):
        if not any(w in q for w in _BUSINESS_BLOCK_WORDS):
            return "smalltalk"
    return None


def _keyword_fallback(question: str) -> str:
    q = question.lower()
    if _is_identity_question(question) and not any(w in q for w in _STRONG_HUMAN_WORDS):
        return "smalltalk"  # 兜底通道同样不给身份疑问句贴 human
    for labels, kws in _KEYWORD_RULES:
        for kw in kws:
            if kw in q:
                return labels[0]
    return "faq"


def _extract_order_no(question: str) -> str | None:
    m = re.search(r"[A-Za-z]{2,}\d{6,}", question)  # 形如 AGS202609170001
    return m.group(0) if m else None


def _parse_json(text: str) -> dict:
    text = re.sub(r"^```(?:json)?|```$", "", (text or "").strip(), flags=re.MULTILINE).strip()
    data = json.loads(text)
    return data if isinstance(data, dict) else {}


def classify_full(question: str, llm) -> IntentDecision:
    """返回意图 + 订单号 + 导购偏好（keyword / maxPrice / inDomain）的完整判定。

    顺序：高置信规则前置（human / 纯寒暄，不调 LLM）→ LLM 一次调用同时出意图与偏好
    → 关键词兜底（偏好字段留空，表示"无判定"）。
    """
    order_no = _extract_order_no(question)

    # 1. 高置信规则前置：省一次 LLM 往返（实测寒暄首 token 7 316 → 2 256 ms）
    hard = _hard_rule(question)
    if hard is not None:
        return IntentDecision(hard, order_no)

    # 2. LLM 语义分类 + 偏好抽取（合并为一次调用，导购分支少一次串行往返）
    try:
        response = llm.invoke([SystemMessage(content=_INTENT_PROMPT), _human_message(question)])
        data = _parse_json(response.content or "")
        intent = str(data.get("intent", "")).strip().lower()
        if intent in INTENT_LABELS:
            keyword = data.get("keyword")
            keyword = str(keyword).strip() if keyword and str(keyword).strip() else None
            max_price = data.get("maxPrice")
            max_price = float(max_price) if isinstance(max_price, (int, float)) else None
            in_domain = data.get("inDomain") if isinstance(data.get("inDomain"), bool) else None
            return IntentDecision(intent, data.get("order_no") or order_no,
                                  keyword, max_price, in_domain, True)
    except Exception:
        pass  # 走关键词兜底

    # 3. 关键词兜底：路由不中断；偏好留 None，导购沿用原三级降级（故障时行为与改动前一致）
    return IntentDecision(_keyword_fallback(question), order_no)


def classify_intent(question: str, llm) -> tuple[str, str | None]:
    """兼容签名：只取（意图, 订单号）。导购链路需要偏好时用 classify_full。"""
    decision = classify_full(question, llm)
    return decision.intent, decision.order_no


def _human_message(content: str) -> BaseMessage:
    from langchain_core.messages import HumanMessage

    return HumanMessage(content=content)


# ---------- 多诉求检测（智能体覆盖保证的确定性判据，零 LLM 成本） ----------
# 实测（实验七）：一句多诉求的探针 3 条里 1 条模型只调了一个工具——"取到资料就提前退出"
# 的优化把第二步规划砍掉了，覆盖与否完全靠模型自觉。这里用连接词/问句切分做确定性计数：
# 诉求条数 > 已执行工具数时跳过提前退出，让既有的第二轮规划变成"补齐核对轮"。
# 单诉求消息完全不受影响（不付第二轮 6~15s 的代价）。
_MULTI_DEMAND_SPLIT = re.compile(r"顺便|另外|以及|同时|还想问|再帮我|[？?；;。！!]")
_DEMAND_MIN_CHARS = 4


def count_demands(question: str) -> int:
    """按连接词与句读切分，返回诉求条数（≥1）；只是粗计数，供"是否补一轮规划"决策用"""
    segments = [x.strip() for x in _MULTI_DEMAND_SPLIT.split(question or "") if len(x.strip()) >= _DEMAND_MIN_CHARS]
    return max(1, len(segments))


def looks_multi_demand(question: str) -> bool:
    return count_demands(question) > 1
