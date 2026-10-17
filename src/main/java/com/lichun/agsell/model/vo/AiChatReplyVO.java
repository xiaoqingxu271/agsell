package com.lichun.agsell.model.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 客服对话响应（Java → 小程序）
 * 透传 Python 服务返回的会话 ID、回复文本与下一轮建议问题
 */
@Data
public class AiChatReplyVO {

    /** 会话 ID */
    private String sessionId;

    /** AI 回复文本 */
    private String reply;

    /** 下一轮建议问题（小贴士），点击可直接发送 */
    private List<String> suggestions;

    /**
     * 导购推荐的结构化商品卡（id/名称/价格/主图/产地/销量/好评率/好评词）。
     * 只有 reply 文本时，用户看完推荐还得自己去搜——导购链路在 UI 上等于断掉；
     * 流式接口走 SSE 的 items 事件下发，本字段是同步接口的同口径出口。
     */
    private List<Object> items;
}
