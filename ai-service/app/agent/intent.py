"""意图识别：高置信规则前置 + LLM 分类 + 关键词兜底

三级路由（成本从低到高，命中即返回，最大化省掉 LLM 往返）：
1. _hard_rule：人工强诉求 / 纯寒暄 —— 高置信、零成本、毫秒级，直接命中；
2. LLM：输出 JSON 完成语义分类（含 few-shot 易混示例）；
3. _keyword_fallback：LLM 不可用时的关键词规则兜底，保证路由不中断。

意图标签：faq / order / logistics / after_sales / smalltalk / human / recommend
"""
import json
import re

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
- "谢谢你，我的退款到哪了" → after_sales（虽含"谢谢"，但核心是查退款，按业务意图判，不是 smalltalk）
- "你好，我想查下订单" → order（寒暄后带业务诉求，按业务意图判，不是 smalltalk）
- "哪种茶叶比较好""销量最高的商品是哪些""有没有便宜又好吃的水果""第一次买不知道买什么好" → recommend
- "叫你们领导来""我要找真人客服""你们要负责任" → human
- "我下的那单蜂蜜是什么状态""上周那笔订单什么情况" → order（问具体订单状态，不是 faq）
- "我买的米怎么还显示未发货""什么时候能送到""包裹揽收了吗" → logistics（问发货/配送进度，不是 order）
- "换货的那单处理好了吗""帮我看看退款审核到哪一步""赔偿金额什么时候给我" → after_sales（问售后/退款进度，不是 logistics/faq）
- "怎么退货""支持七天无理由退货吗" → faq（咨询退换货"政策/规则"，而非查询某笔售后单进度）

输出格式：{"intent": "<标签>", "order_no": "<消息中提到的订单号，没有则为 null>"}
只输出 JSON。"""

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

# 人工强诉求词：只要出现即转人工（在 103 题测试集上对其他意图零误伤）。
# 不含"赔偿"——"赔偿金额什么时候给我"属于售后进度查询，应由 after_sales 处理。
_HARD_HUMAN_WORDS = (
    "投诉", "举报", "曝光", "315", "人工", "真人", "领导", "监管", "爆料", "欺诈", "售假", "说法", "负责",
)

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


def _hard_rule(question: str) -> str | None:
    """高置信规则前置。命中返回意图标签，否则返回 None（交给 LLM）。"""
    q = question.lower()
    if any(w in q for w in _HARD_HUMAN_WORDS):
        return "human"
    if len(question.strip()) <= _SMALLTALK_MAX_LEN and any(w in q for w in _SMALLTALK_WORDS):
        if not any(w in q for w in _BUSINESS_BLOCK_WORDS):
            return "smalltalk"
    return None


def _keyword_fallback(question: str) -> str:
    for labels, kws in _KEYWORD_RULES:
        for kw in kws:
            if kw in question.lower():
                return labels[0]
    return "faq"


def _extract_order_no(question: str) -> str | None:
    m = re.search(r"[A-Za-z]{2,}\d{6,}", question)  # 形如 AGS202609170001
    return m.group(0) if m else None


def classify_intent(question: str, llm) -> tuple[str, str | None]:
    """返回 (intent, order_no)。

    顺序：高置信规则前置（human/纯 smalltalk，不调 LLM）→ LLM 分类 → 关键词兜底。
    """
    order_no = _extract_order_no(question)

    # 1. 高置信规则前置：人工诉求 / 纯寒暄直接命中，省一次 LLM 往返（降延迟、省额度）
    hard = _hard_rule(question)
    if hard is not None:
        return hard, order_no

    # 2. LLM 语义分类
    try:
        response = llm.invoke(
            [SystemMessage(content=_INTENT_PROMPT), _human_message(question)]
        )
        text = (response.content or "").strip()
        text = re.sub(r"^```(?:json)?|```$", "", text, flags=re.MULTILINE).strip()
        data = json.loads(text)
        intent = str(data.get("intent", "")).strip().lower()
        if intent in INTENT_LABELS:
            return intent, data.get("order_no") or order_no
    except Exception:
        pass  # 走关键词兜底

    # 3. 关键词兜底，保证路由不中断
    return _keyword_fallback(question), order_no


def _human_message(content: str) -> BaseMessage:
    from langchain_core.messages import HumanMessage

    return HumanMessage(content=content)
