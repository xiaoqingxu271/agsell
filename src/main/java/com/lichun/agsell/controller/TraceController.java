package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.vo.ProductTraceSummaryVO;
import com.lichun.agsell.model.vo.TracePublicVO;
import com.lichun.agsell.service.TraceabilityService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端-产地溯源（无需登录）
 * 扫码/详情页入口查询溯源档案
 */
@Tag(name = "用户端-产地溯源", description = "按批次号查询溯源档案、商品溯源摘要")
@RestController
@RequestMapping("/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceabilityService traceabilityService;

    @Operation(summary = "按批次号查询溯源档案")
    @GetMapping
    public BaseResponse<TracePublicVO> getTrace(@RequestParam String batchNo) {
        return ResultUtils.success(traceabilityService.getPublicTrace(batchNo));
    }

    @Operation(summary = "商品溯源摘要（详情页溯源入口）")
    @GetMapping("/product/{productId}")
    public BaseResponse<ProductTraceSummaryVO> getProductTraceSummary(@PathVariable Long productId) {
        return ResultUtils.success(traceabilityService.getProductTraceSummary(productId));
    }
}
