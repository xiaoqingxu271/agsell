package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.AdminAfterSalesHandleRequest;
import com.lichun.agsell.model.vo.AdminAfterSalesDetailVO;
import com.lichun.agsell.model.vo.AdminAfterSalesListItemVO;
import com.lichun.agsell.service.AdminAfterSalesService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端-售后管理
 */
@Tag(name = "管理端-售后管理", description = "售后单列表、详情、处理（同意退款/拒绝）")
@RestController
@RequestMapping("/admin/after-sales")
@RequiredArgsConstructor
public class AdminAfterSalesController {

    private final AdminAfterSalesService adminAfterSalesService;

    @Operation(summary = "售后单列表")
    @GetMapping("/list")
    public BaseResponse<Page<AdminAfterSalesListItemVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String afterSalesNo,
            @RequestParam(required = false) String username) {
        return ResultUtils.success(adminAfterSalesService.list(pageNum, pageSize, status, afterSalesNo, username));
    }

    @Operation(summary = "售后单详情")
    @GetMapping("/{afterSalesNo}")
    public BaseResponse<AdminAfterSalesDetailVO> getDetail(@PathVariable String afterSalesNo) {
        return ResultUtils.success(adminAfterSalesService.getDetail(afterSalesNo));
    }

    @Operation(summary = "处理售后（同意退款/拒绝）")
    @PostMapping("/{afterSalesNo}/handle")
    public BaseResponse<Void> handle(@PathVariable String afterSalesNo,
                                     @RequestBody AdminAfterSalesHandleRequest request) {
        adminAfterSalesService.handle(afterSalesNo, request);
        return ResultUtils.success(null);
    }
}
