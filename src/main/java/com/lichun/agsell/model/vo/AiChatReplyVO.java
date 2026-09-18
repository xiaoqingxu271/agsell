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
}
