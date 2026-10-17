"""意图层：高置信规则前置 + 关键词表扩充 + 偏好合并产出（纯规则部分不依赖网络/LLM）

验收点：
1. human 强诉求 / 纯寒暄 / 身份疑问句由规则前置直出，根本不调用 LLM（省往返、降延迟）；
2. 寒暄夹带业务诉求时不得前置，必须交给 LLM 判业务意图；
3. LLM 不可用时，扩充后的关键词兜底能覆盖导购/物流/售后易混问法；
4. classify_full 一次调用同时给出意图与导购偏好（keyword/maxPrice/inDomain），
   且"没有判定"必须表达为 None，而不是被下游误当成"域外"去拒答。
"""
import json

from langchain_core.messages import AIMessage

from app.agent.intent import _hard_rule, _keyword_fallback, classify_full, classify_intent


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


# ---------- 身份疑问句：规则假阳性修复（量化实验二实测） ----------
def test_identity_question_is_smalltalk_not_human():
    """"你是真人还是智能助手"曾被 "真人" 硬判 human（纯大模型判对了 smalltalk）。

    现在由规则直接判 smalltalk：既修掉假阳性，又把这类问法的一次 LLM 调用省掉。
    """
    for q in ["你是真人还是智能助手", "你是机器人吗", "你是不是机器人", "你是AI吗",
              "你是人工客服吗", "你是什么智能"]:
        assert _hard_rule(q) == "smalltalk", q
        intent, _ = classify_intent(q, ExplodingLLM())  # 全程不调 LLM
        assert intent == "smalltalk", q


def test_real_human_demands_still_hard_route_to_human():
    """修复不能把真实诉求一起放掉——这些必须仍然 100% 前置转人工。"""
    for q in ["我要找真人客服", "给我转个人工", "叫你们领导来", "你们要负责任", "我要投诉",
              "帮我转接人工客服", "再不去解决我就曝光你们"]:
        assert _hard_rule(q) == "human", q
        assert _keyword_fallback(q) == "human", q


def test_complaint_wrapped_in_identity_question_still_human():
    """身份疑问 + 真实强诉求共存时，转人工优先（不能因为出现"你是机器人"就降级成寒暄）。"""
    assert _hard_rule("你是机器人吗？我要投诉你们") == "human"


def test_no_hard_rule_for_semantic_human_ask():
    """"我不想跟机器说了，找个活人"没有任何词面强诉求词，不前置，交给大模型判。

    实验二实测：这类句子正是大模型的价值所在——纯规则 21.4% human 召回，
    而生产路径在这条上判对。把它硬编码进词表会让规则层再次过拟合。
    """
    assert _hard_rule("我不想跟机器说了，找个活人") is None


def test_identity_guard_applies_to_keyword_fallback_too():
    """LLM 故障走兜底时，身份疑问句同样不能被"真人/机器人"贴成 human。"""
    assert _keyword_fallback("你是真人还是智能助手") == "smalltalk"


# ---------- 合并调用：一次 LLM 同时产出意图与导购偏好 ----------
class MergedLLM:
    def __init__(self, payload: dict):
        self.payload = payload
        self.calls = 0

    def invoke(self, messages):
        self.calls += 1
        return AIMessage(content=json.dumps(self.payload, ensure_ascii=False))


def test_classify_full_returns_prefs_in_one_call():
    llm = MergedLLM({
        "intent": "recommend", "order_no": None,
        "keyword": "茶叶", "maxPrice": 80, "inDomain": True,
    })
    d = classify_full("有没有适合送人的茶叶，80 块以内", llm)
    assert (d.intent, d.keyword, d.max_price, d.in_domain, d.from_llm) == (
        "recommend", "茶叶", 80.0, True, True
    )
    assert llm.calls == 1  # 关键：导购偏好不再需要第二次抽取调用


def test_classify_full_non_recommend_leaves_prefs_empty():
    llm = MergedLLM({"intent": "order", "order_no": None, "keyword": None, "maxPrice": None, "inDomain": None})
    d = classify_full("我的订单到哪了", llm)
    assert d.intent == "order" and d.keyword is None and d.in_domain is None


def test_classify_full_invalid_json_means_no_judgement():
    """LLM 输出非法 → 关键词兜底保路由，但偏好字段一律留空（None=没有判定，不能据此拒答）"""
    d = classify_full("有什么水果推荐", BadLLM())
    assert d.intent == "recommend"
    assert (d.keyword, d.max_price, d.in_domain, d.from_llm) == (None, None, None, False)


def test_classify_full_hard_rule_skips_llm_entirely():
    llm = MergedLLM({"intent": "faq"})
    d = classify_full("你好", llm)
    assert d.intent == "smalltalk" and llm.calls == 0 and d.from_llm is False
