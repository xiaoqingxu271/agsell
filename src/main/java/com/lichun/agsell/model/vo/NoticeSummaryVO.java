package com.lichun.agsell.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理端通知汇总：导航栏铃铛下拉展示的待办事项数量
 */
@Data
@Schema(description = "管理端通知汇总")
public class NoticeSummaryVO {

    @Schema(description = "待发货订单数")
    private long pendingShipCount;

    @Schema(description = "低库存商品数")
    private long lowStockCount;

    @Schema(description = "待处理售后数")
    private long pendingAfterSalesCount;

    @Schema(description = "待办总数（红点显示）")
    private long total;
}
