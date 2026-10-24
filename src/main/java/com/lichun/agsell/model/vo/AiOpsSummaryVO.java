package com.lichun.agsell.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 近 N 日 AI 运营汇总（对话量/拒答率/工具分布/按日趋势）
 * <p>
 * Python 侧 chat_log 聚合（每轮一条，main.py 落库），Java 只透传；
 * 无数据时 Python 返回零值结构，前端显示空态。
 */
@Data
@Schema(description = "近 N 日 AI 运营汇总")
public class AiOpsSummaryVO {

    @Schema(description = "统计窗口天数")
    private Integer days;

    @Schema(description = "对话轮数")
    private Long totalTurns;

    @Schema(description = "拒答轮数（两道闸门命中）")
    private Long refusedTurns;

    @Schema(description = "拒答率 0~1")
    private Double refusalRate;

    @Schema(description = "工具调用分布（工具名 → 次数，降序）")
    private Map<String, Long> byTool;

    @Schema(description = "按日对话量趋势（缺数日补零）")
    private List<DailyCount> daily;

    @Data
    @Schema(description = "按日对话量")
    public static class DailyCount {

        @Schema(description = "日期（ISO，本地时区）")
        private String date;

        @Schema(description = "对话轮数")
        private Long count;
    }
}
