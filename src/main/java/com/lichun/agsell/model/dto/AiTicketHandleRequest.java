package com.lichun.agsell.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 工单处理请求（管理端）
 */
@Data
@Schema(description = "AI 工单处理请求")
public class AiTicketHandleRequest {

    @Schema(description = "处理备注（如何跟进/联系结果）")
    private String remark;
}
