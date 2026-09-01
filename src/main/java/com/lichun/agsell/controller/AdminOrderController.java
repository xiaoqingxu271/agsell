package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.OrderShipRequest;
import com.lichun.agsell.model.vo.AdminOrderDetailVO;
import com.lichun.agsell.model.vo.AdminOrderListItemVO;
import com.lichun.agsell.service.AdminOrderService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-订单管理", description = "订单列表、详情、发货")
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @Operation(summary = "订单列表")
    @GetMapping("/list")
    public BaseResponse<Page<AdminOrderListItemVO>> listOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String username) {
        return ResultUtils.success(adminOrderService.listOrders(pageNum, pageSize, status, orderNo, username));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public BaseResponse<AdminOrderDetailVO> getOrderDetail(@PathVariable String orderNo) {
        return ResultUtils.success(adminOrderService.getOrderDetail(orderNo));
    }

    @Operation(summary = "发货")
    @PostMapping("/{orderNo}/ship")
    public BaseResponse<Void> shipOrder(@PathVariable String orderNo,
                                         @RequestBody OrderShipRequest request) {
        adminOrderService.shipOrder(orderNo, request);
        return ResultUtils.success(null);
    }
}
