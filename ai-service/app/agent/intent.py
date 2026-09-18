"""意图识别：LLM 分类 + 关键词兜底

意图标签：faq / order / logistics / after_sales / smalltalk / human
"""
import json
import re

from langchain_core.messages import SystemMessage
from langchain_core.messages.base import BaseMessage

INTENT_LABELS = ("faq", "order", "logistics", "after_sales", "smalltalk", "human")

_INTENT_PROMPT = """你是电商客服的意图识别器。只输出一个 JSON 对象，不要输出任何其他内容。

根据用户消息判断意图，可选标签：
- faq：询问平台规则/商品/运费/售后政策等常识问题（如"运费多少""怎么退货"）
- order：查询订单状态/订单列表（如"我的订单""订单到哪了"）
- logistics：查询物流/发货进度（如"发货没有""快递到哪了"）
- after_sales：查询售后/退款进度（如"退款到哪了""售后处理得怎么样"）
- smalltalk：寒暄、打招呼、闲聊（如"你好""谢谢"）
- human：需要人工处理（投诉、情绪激烈、要求赔偿、涉及账号隐私或复杂纠纷）

输出格式：{"intent": "<标签>", "order_no": "<消息中提到的订单号，没有则为 null>"}
只输出 JSON。"""

_KEYWORD_RULES = [
    (("human",), ("投诉", "举报", "曝光", "315", "我要人工", "转人工", "叫人工")),
    (("logistics",), ("物流", "发货", "快递", "配送", "到哪", "运单", "签收")),
    (("after_sales",), ("退款", "售后", "退货", "退换", "仅退款", "坏果", "理赔")),
    (("order",), ("订单", "下单", "买过", "购买记录", "待发货", "待收货", "确认收货")),
    (("smalltalk",), ("你好", "您好", "hi", "hello", "谢谢", "再见", "在吗")),
]


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
    """返回 (intent, order_no)。LLM 失败时用关键词兜底，保证路由不中断。"""
    order_no = _extract_order_no(question)
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
    return _keyword_fallback(question), order_no


def _human_message(content: str) -> BaseMessage:
    from langchain_core.messages import HumanMessage

    return HumanMessage(content=content)
