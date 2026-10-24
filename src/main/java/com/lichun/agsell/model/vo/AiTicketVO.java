package com.lichun.agsell.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 转人工工单 VO（管理端）
 */
@Data
@Schema(description = "AI 转人工工单")
public class AiTicketVO {

    @Schema(description = "工单ID")
    private Long id;

    @Schema(description = "用户ID（游客为 NULL）")
    private Long userId;

    @Schema(description = "AI 会话号")
    private String sessionId;

    @Schema(description = "触发转人工的用户原话")
    private String question;

    @Schema(description = "转人工原因")
    private String reason;

    @Schema(description = "状态 0=待处理 1=已处理")
    private Integer status;

    @Schema(description = "状态文案")
    private String statusText;

    @Schema(description = "处理备注")
    private String handleRemark;

    @Schema(description = "处理管理员ID")
    private Long handleBy;

    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
