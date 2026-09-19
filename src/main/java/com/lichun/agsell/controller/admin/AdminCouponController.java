package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.annotation.OperationLog;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.CouponCreateRequest;
import com.lichun.agsell.model.vo.CouponVO;
import com.lichun.agsell.service.CouponService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端-优惠券管理
 */
@Tag(name = "管理端-优惠券", description = "优惠券模板管理（建档/编辑/上下架/删除）")
@RestController
@RequestMapping("/admin/coupon")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    @Operation(summary = "券模板分页查询")
    @GetMapping("/page")
    public BaseResponse<Page<CouponVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ResultUtils.success(couponService.pageCoupons(pageNum, pageSize, keyword, status));
    }

    @Operation(summary = "创建券模板")
    @PostMapping
    @OperationLog(module = "优惠券管理", action = "新增优惠券",
            content = "新增了优惠券「#{#request.couponName}」")
    public BaseResponse<Map<String, Long>> create(@RequestBody CouponCreateRequest request) {
        Long id = couponService.createCoupon(request);
        return ResultUtils.success(Map.of("id", id));
    }

    @Operation(summary = "编辑券模板")
    @PutMapping("/{id}")
    @OperationLog(module = "优惠券管理", action = "编辑优惠券",
            content = "编辑了优惠券 #{#id}")
    public BaseResponse<Void> update(@PathVariable Long id, @RequestBody CouponCreateRequest request) {
        couponService.updateCoupon(id, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除券模板")
    @DeleteMapping("/{id}")
    @OperationLog(module = "优惠券管理", action = "删除优惠券",
            content = "删除了优惠券 #{#id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "券模板上下架")
    @PutMapping("/{id}/status")
    @OperationLog(module = "优惠券管理", action = "优惠券上下架",
            content = "优惠券 #{#id} 已#{#status == 1 ? '上架' : '下架'}")
    public BaseResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        couponService.updateCouponStatus(id, status);
        return ResultUtils.success(null);
    }
}
