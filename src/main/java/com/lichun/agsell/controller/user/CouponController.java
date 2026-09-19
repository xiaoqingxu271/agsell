package com.lichun.agsell.controller.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.vo.CouponAvailableVO;
import com.lichun.agsell.model.vo.CouponListItemVO;
import com.lichun.agsell.model.vo.UserCouponVO;
import com.lichun.agsell.service.CouponService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户端-优惠券
 */
@Tag(name = "用户端-优惠券", description = "领券中心、我的券包、结算页可用券")
@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "领券中心：可领券列表（游客可浏览；已领标记仅在登录时返回）")
    @GetMapping("/list")
    public BaseResponse<List<CouponListItemVO>> list() {
        return ResultUtils.success(couponService.listCoupons());
    }

    @Operation(summary = "领取优惠券")
    @PostMapping("/{id}/receive")
    public BaseResponse<UserCouponVO> receive(@PathVariable Long id) {
        return ResultUtils.success(couponService.receiveCoupon(id));
    }

    @Operation(summary = "我的券包（status=0 未使用 1 已使用 2 已过期，不传查全部）")
    @GetMapping("/my")
    public BaseResponse<Page<UserCouponVO>> my(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status) {
        return ResultUtils.success(couponService.myCoupons(pageNum, pageSize, status));
    }

    @Operation(summary = "结算页可用券（按订单金额+商品范围过滤，usable/unusable 两组）")
    @GetMapping("/available")
    public BaseResponse<CouponAvailableVO> available(@RequestParam BigDecimal totalAmount,
                                                     @RequestParam(required = false) List<Long> productIds) {
        return ResultUtils.success(couponService.listAvailable(totalAmount, productIds));
    }
}
