package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.AfterSalesCreateRequest;
import com.lichun.agsell.model.vo.AfterSalesDetailVO;
import com.lichun.agsell.model.vo.AfterSalesListItemVO;
import com.lichun.agsell.service.AfterSalesService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端-售后接口
 */
@Tag(name = "用户端-售后接口", description = "申请售后、我的售后列表、售后详情、撤销售后")
@RestController
@RequestMapping("/after-sales")
@RequiredArgsConstructor
public class AfterSalesController {

    private final AfterSalesService afterSalesService;

    @Operation(summary = "申请售后")
    @PostMapping
    public BaseResponse<AfterSalesDetailVO> apply(@RequestBody AfterSalesCreateRequest request) {
        return ResultUtils.success(afterSalesService.apply(request));
    }

    @Operation(summary = "我的售后列表")
    @GetMapping("/list")
    public BaseResponse<Page<AfterSalesListItemVO>> listMine(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status) {
        return ResultUtils.success(afterSalesService.listMine(pageNum, pageSize, status));
    }

    @Operation(summary = "售后详情")
    @GetMapping("/{afterSalesNo}")
    public BaseResponse<AfterSalesDetailVO> getDetail(@PathVariable String afterSalesNo) {
        return ResultUtils.success(afterSalesService.getDetail(afterSalesNo));
    }

    @Operation(summary = "撤销售后申请")
    @PostMapping("/{afterSalesNo}/cancel")
    public BaseResponse<Void> cancelApply(@PathVariable String afterSalesNo) {
        afterSalesService.cancelApply(afterSalesNo);
        return ResultUtils.success(null);
    }
}
