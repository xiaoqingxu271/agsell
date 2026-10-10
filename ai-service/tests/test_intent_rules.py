"""阶段七：意图高置信规则前置 + 关键词表扩充（纯规则，不依赖网络/LLM）

验收点：
1. human 强诉求 / 纯寒暄由规则前置直出，根本不调用 LLM（省往返、降延迟）；
2. 寒暄夹带业务诉求时不得前置，必须交给 LLM 判业务意图；
3. LLM 不可用时，扩充后的关键词兜底能覆盖导购/物流/售后易混问法。
"""
import json

from langchain_core.messages import AIMessage

from app.agent.intent import _hard_rule, _keyword_fallback, classify_intent


class ExplodingLLM:
    """规则前置命中的输入若触发 LLM 调用即判失败。"""

    def invoke(self, messages):
        raise AssertionError("高置信规则输入不应调用 LLM")


class FixedLLM:
    def __init__(self, intent: str):
        self.intent = intent

    def invoke(self, messages):
        return AIMessage(content=json.dumps({"intent": self.intent, "order_no": None}))


class BadLLM:
    def invoke(self, messages):
        return AIMessage(content="我不是JSON")  # 触发关键词兜底


# ---------- human 高置信前置 ----------
def test_human_hard_rule_bypasses_llm():
    for q in [
        "转人工", "叫你们领导来", "我要找真人客服", "帮我转接人工客服",
        "再不解决我就去315投诉", "我要举报售假", "你们这是欺诈我要曝光",
        "吃了你们的坏果拉肚子了，你们要负责任", "太过分了给我个说法",
    ]:
        assert _hard_rule(q) == "human", q
        intent, _ = classify_intent(q, ExplodingLLM())
        assert intent == "human", q


# ---------- smalltalk 纯寒暄前置 ----------
def test_smalltalk_hard_rule_bypasses_llm():
    for q in [
        "你好", "您好呀", "在吗", "谢谢你", "再见", "早上好",
        "辛苦啦", "麻烦你了", "好的，知道了", "今天心情不错",
        "你叫什么名字", "你是机器人吗",
    ]:
        assert _hard_rule(q) == "smalltalk", q
        intent, _ = classify_intent(q, ExplodingLLM())
        assert intent == "smalltalk", q


# ---------- 寒暄夹带业务 → 不前置，交 LLM ----------
def test_greeting_with_business_not_hard_routed():
    # 含业务否决词，即便短句也不前置 smalltalk
    assert _hard_rule("谢谢你，我的退款到哪了") is None
    assert _hard_rule("你好，我想查下订单") is None
    assert _hard_rule("客服在吗") is None
    # 交给 LLM 后按业务意图分类
    intent, _ = classify_intent("谢谢你，我的退款到哪了", FixedLLM("after_sales"))
    assert intent == "after_sales"


def test_long_greeting_like_sentence_not_hard_routed():
    # 超长短句即便含寒暄词也不前置（更可能夹带业务描述）
    assert _hard_rule("你好呀我想问一下最近有什么优惠活动吗") is None


# ---------- 售后进度不应被 human 误伤 ----------
def test_aftersales_progress_not_misrouted_to_human():
    # "赔偿金额什么时候给我"是售后进度查询，不含人工强诉求词
    assert _hard_rule("赔偿金额什么时候给我") is None
    intent, _ = classify_intent("赔偿金额什么时候给我", FixedLLM("after_sales"))
    assert intent == "after_sales"


# ---------- 关键词兜底扩充（LLM 失败通道）----------
def test_keyword_fallback_recommend_expanded():
    for q in [
        "销量最高的商品是哪些", "哪种茶叶比较好", "有没有便宜又好吃的水果",
        "帮我挑个礼物送长辈", "50元以内有什么好物", "帮我选一款大米",
    ]:
        assert _keyword_fallback(q) == "recommend", q
    # 端到端：LLM 非法输出 → 兜底也能判导购
    assert classify_intent("哪种茶叶比较好", BadLLM())[0] == "recommend"


def test_keyword_fallback_logistics_aftersales_expanded():
    # 物流新增：送到/包裹/揽收
    assert _keyword_fallback("什么时候能送到") == "logistics"
    assert _keyword_fallback("我的包裹怎么还在派送") == "logistics"
    assert _keyword_fallback("包裹显示揽收了吗") == "logistics"
    # 售后新增：换货/赔偿；且售后优先于物流词"到哪"
    assert _keyword_fallback("换货的那单处理好了吗") == "after_sales"
    assert _keyword_fallback("赔偿金额什么时候给我") == "after_sales"
    assert _keyword_fallback("我的退款到哪一步了") == "after_sales"


# ---------- 规则前置不影响订单号抽取 ----------
def test_order_no_extracted_under_hard_rule():
    intent, order_no = classify_intent(
        "转人工，我订单 AGS202609170001 有问题", ExplodingLLM()
    )
    assert intent == "human"
    assert order_no == "AGS202609170001"
