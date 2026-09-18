package com.lichun.agsell.model.dto;

import lombok.Data;

/**
 * AI 客服对话请求（小程序 → Java）
 * userId 由服务端从 JWT 解析，不信任前端传入
 */
@Data
public class AiChatRequest {

    /** 会话 ID（前端生成并透传，用于多轮上下文） */
    private String sessionId;

    /** 用户消息 */
    private String message;
}
