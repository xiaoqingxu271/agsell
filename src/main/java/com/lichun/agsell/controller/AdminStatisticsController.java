package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.vo.AdminStatisticsVO;
import com.lichun.agsell.model.vo.StatisticsTrendVO;
import com.lichun.agsell.service.AdminStatisticsService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-数据统计", description = "数据概览核心指标")
@RestController
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {

    private final AdminStatisticsService adminStatisticsService;

    @Operation(summary = "数据概览统计")
    @GetMapping("/overview")
    public BaseResponse<AdminStatisticsVO> getOverview() {
        return ResultUtils.success(adminStatisticsService.getOverview());
    }

    @Operation(summary = "数据趋势统计（近 N 天新增用户/订单数/销售额）")
    @GetMapping("/trend")
    public BaseResponse<StatisticsTrendVO> getTrend(@RequestParam(defaultValue = "7") int days) {
        return ResultUtils.success(adminStatisticsService.getTrend(days));
    }
}
