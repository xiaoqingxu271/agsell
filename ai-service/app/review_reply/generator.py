"""评价智能回复生成器（管理端「AI 生成回复」草稿）

方法：LLM 按口碑标签自适应语气（好评→感谢回购 / 中评→感谢改进 / 差评→道歉补偿）
+ 规则模板兜底。与项目「LLM + 兜底」双通道哲学一致——Agnes 限流/超时/断网时
降级为确定性模板草稿，管理端操作不中断。
"""
from langchain_core.messages import HumanMessage, SystemMessage

REVIEW_REPLY_PROMPT = """你是「{platform}」农产品电商平台的卖家客服，正在为一条买家评价撰写公开回复。

买家评价信息：
- 商品：{product_name}
- 评分：{rating} 星（满分 5 星）
- 口碑标签：{sentiment_text}
- 评价内容：{content}

回复要求：
1. 以"亲"开头，语气真诚自然、口语化，100 字以内；
2. {tone_instruction}
3. 涉及退款/补发/补偿时，引导买家联系在线客服或拨打客服电话 {service_phone}，不要自行承诺具体金额；
4. 只输出回复正文，不要输出引号、前缀、署名或任何解释。"""

_TONE_POS = "真诚感谢买家的好评，简要呼应评价中提到的优点，并欢迎再次回购；"
_TONE_NEU = "感谢买家反馈，正面承认存在的不足，说明具体改进方向（品质/包装/物流），并欢迎再次下单体验；"
_TONE_NEG = "先真诚道歉，针对买家提到的问题主动给出解决路径（坏果包赔/退款/补发），态度诚恳，切勿辩解或推卸责任。"

_SENTIMENT_TEXT = {1: "好评", 0: "中评", -1: "差评"}

# 兜底模板（确定性输出；{product} 缺省时退化为通用话术）
_FALLBACK_POS = "亲，感谢您的认可与支持！我们会继续严把品质关，把更新鲜、更实惠的{product}送到您手中，期待您的再次光临～"
_FALLBACK_NEU = "亲，感谢您的反馈，很抱歉这次没能让您完全满意。我们已将您的建议同步给品控和物流团队持续改进，期待下次给您带来更好的体验～"
_FALLBACK_NEG = (
    "亲，非常抱歉给您带来了不好的体验！本店生鲜坏果包赔，"
    "麻烦您联系在线客服或拨打客服电话 {phone}，我们会第一时间为您处理退款或补发，一定给您一个满意的解决方案。"
)

_MAX_REPLY_LEN = 300


def infer_label(sentiment_label: int | None, rating: int | None) -> int:
    """口碑标签推断：优先情感分析结果，缺省按评分先验，再缺省视为中评"""
    if sentiment_label in (1, 0, -1):
        return sentiment_label
    if rating is not None and rating >= 4:
        return 1
    if rating is not None and rating <= 2:
        return -1
    return 0


def _fallback_reply(label: int, product_name: str | None, service_phone: str) -> str:
    if label == 1:
        product = product_name.strip() if product_name and product_name.strip() else "好物"
        return _FALLBACK_POS.format(product=product)
    if label == -1:
        return _FALLBACK_NEG.format(phone=service_phone)
    return _FALLBACK_NEU


def build_reply_prompt(
    content: str,
    rating: int | None,
    sentiment_label: int | None,
    product_name: str | None,
    platform: str,
    service_phone: str,
) -> str:
    label = infer_label(sentiment_label, rating)
    tone = {1: _TONE_POS, 0: _TONE_NEU, -1: _TONE_NEG}[label]
    return REVIEW_REPLY_PROMPT.format(
        platform=platform,
        product_name=(product_name or "").strip() or "（未知商品）",
        rating=rating if rating is not None else "未评分",
        sentiment_text=_SENTIMENT_TEXT.get(label, "中评"),
        content=(content or "").strip() or "（无文字内容）",
        tone_instruction=tone,
        service_phone=service_phone,
    )


def generate_reply(
    content: str,
    rating: int | None,
    sentiment_label: int | None,
    product_name: str | None,
    llm,
    platform: str,
    service_phone: str,
) -> dict:
    """生成一条回复草稿。返回 {"reply": str, "source": "llm" | "fallback"}。

    LLM 失败/输出为空/超长时降级为规则模板，不抛异常（管理端操作不中断）。
    """
    prompt = build_reply_prompt(content, rating, sentiment_label, product_name, platform, service_phone)
    try:
        response = llm.invoke([SystemMessage(content=prompt), HumanMessage(content="请生成回复")])
        text = (response.content or "").strip()
        if 5 <= len(text) <= _MAX_REPLY_LEN:
            return {"reply": text, "source": "llm"}
    except Exception:
        pass
    label = infer_label(sentiment_label, rating)
    return {"reply": _fallback_reply(label, product_name, service_phone), "source": "fallback"}
