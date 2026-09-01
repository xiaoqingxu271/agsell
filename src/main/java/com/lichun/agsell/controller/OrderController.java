package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.OrderCreateRequest;
import com.lichun.agsell.model.vo.OrderCreateVO;
import com.lichun.agsell.model.vo.OrderDetailVO;
import com.lichun.agsell.model.vo.OrderListItemVO;
import com.lichun.agsell.service.OrderService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户端-订单接口", description = "订单创建、列表、详情、取消、确认收货")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "提交订单")
    @PostMapping("/create")
    public BaseResponse<OrderCreateVO> createOrder(@RequestBody OrderCreateRequest request) {
        return ResultUtils.success(orderService.createOrder(request));
    }

    @Operation(summary = "我的订单列表")
    @GetMapping("/list")
    public BaseResponse<Page<OrderListItemVO>> listOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status) {
        return ResultUtils.success(orderService.listOrders(pageNum, pageSize, status));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public BaseResponse<OrderDetailVO> getOrderDetail(@PathVariable String orderNo) {
        return ResultUtils.success(orderService.getOrderDetail(orderNo));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/{orderNo}/cancel")
    public BaseResponse<Void> cancelOrder(@PathVariable String orderNo,
                                           @RequestParam(required = false) String reason) {
        orderService.cancelOrder(orderNo, reason);
        return ResultUtils.success(null);
    }

    @Operation(summary = "确认收货")
    @PostMapping("/{orderNo}/confirm")
    public BaseResponse<Void> confirmReceive(@PathVariable String orderNo) {
        orderService.confirmReceive(orderNo);
        return ResultUtils.success(null);
    }
}
