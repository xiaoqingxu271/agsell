package com.lichun.agsell.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 转人工工单创建请求（Python 回调 /api/ai/internal/ticket）
 */
@Data
@Schema(description = "AI 转人工工单创建请求")
public class AiTicketCreateRequest {

    @Schema(description = "用户ID（游客为空）")
    private Long userId;

    @Schema(description = "AI 会话号")
    private String sessionId;

    @Schema(description = "触发转人工的用户原话")
    private String question;

    @Schema(description = "转人工原因")
    private String reason;
}
