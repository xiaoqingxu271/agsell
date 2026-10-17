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

    /**
     * 当前正在浏览的商品 ID（从商品详情页进客服时携带）。
     * 公开的商品主键、不含任何归属信息，因此允许前端传入；Python 侧会经
     * /ai/internal/product/search 复核"仅在售"才用作上下文，下架即忽略。
     */
    private Long productId;
}
