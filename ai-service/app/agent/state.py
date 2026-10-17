"""LangGraph Agent 状态定义

智能体改造后只剩三类字段：
- 对话与身份：messages / userId / productId（+ 规则前置的 intent / order_no）
- 本轮取证：observations（唯一表示）+ 由它派生的 rec_items / product_context / product_item
  / human_fallback
- 输出：suggestions

⚠ 旧版还有 tool_type / tool_result / faq_context / rec_keyword / rec_max_price /
rec_in_domain——单分支路由时代"本轮走了哪条支路"的视图。工具编排改成 function calling 后
一轮可以有多条观察，单一标签既不成立、又会与 observations 漂开（两处表示同一事实必然出错），
故整体删除：闸门看 observations[].status，日志与测评看 observations[].tool。
"""
from typing import Annotated, TypedDict

from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    # 规则前置的高置信标签：只有 human / smalltalk 会短路；业务问题一律留空，交给 agent
    intent: str
    order_no: str | None
    userId: int | None
    # 从商详页进客服时由 Java 透传的商品 ID（公开主键、无归属信息，故可由前端携带）
    productId: int | None
    human_fallback: bool
    # 本轮每次工具调用的观察：[{tool,kind,status,payload,items}]
    # status 是闸门信号（ok|empty|refused|error|need_login），生成节点据此决定
    # 正常作答 / 确定性拒答 / 未登录引导 / 故障降级；payload 仅在 status=ok 时非空
    observations: list
    # 导购候选的结构化形态（Java 商品 VO 原样透传，SSE 单独下发给小程序画成可点卡片）。
    # 只给 LLM 文本是不够的：图片/价格/跳转 id 进了 prompt 就丢了，用户看完推荐
    # 还得自己去搜一遍，导购链路在 UI 上等于断掉。
    rec_items: list  # [{id,name,price,mainImage,origin,sales,reviewCount,positiveRate,topKeywords}]
    # 用户当前正在浏览的商品事实与原始商品（整轮只回调 Java 一次）。
    # 目的是让"这个能放多久/适合送礼吗"这类指代问题有依据可答，而不是回一句知识库没覆盖。
    product_context: str
    product_item: dict | None
    # 下一轮建议问题（小贴士）
    suggestions: list  # [str, ...]
