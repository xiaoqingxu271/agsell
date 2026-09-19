package com.lichun.agsell.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.AfterSalesMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.model.dto.AiInternalQueryRequest;
import com.lichun.agsell.model.entity.AfterSales;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.enums.OrderStatusEnum;
import com.lichun.agsell.model.vo.AiAfterSalesVO;
import com.lichun.agsell.model.vo.AiOrderDetailVO;
import com.lichun.agsell.model.vo.AiOrderVO;
import com.lichun.agsell.utils.ResultUtils;
import com.lichun.agsell.utils.ThrowUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 内部接口（仅供 Python AI 客服服务回调，X-Internal-Key 鉴权）
 * <p>
 * 设计要点：
 * 1. 与用户端 JWT 体系隔离，userId 由 Python 侧从会话上下文传入；
 * 2. 越权防护：所有查询强制 eq(userId)，orderNo 必须同时归属该 userId；
 * 3. 隐私最小化：不返回收货人姓名/电话/完整地址，仅返回订单与物流状态。
 */
@Tag(name = "AI 内部接口", description = "供 Python AI 客服服务回调（X-Internal-Key 鉴权）")
@RestController
@RequestMapping("/ai/internal")
@RequiredArgsConstructor
public class AiInternalController {

    private static final Map<Integer, String> AFTER_SALES_TYPE_TEXT = Map.of(
            1, "仅退款",
            2, "退货退款"
    );
    private static final Map<Integer, String> AFTER_SALES_STATUS_TEXT = Map.of(
            0, "待处理",
            1, "已同意",
            2, "已拒绝",
            3, "已撤销"
    );
    private static final Map<String, String> AFTER_SALES_REASON_TEXT = Map.of(
            "QUALITY", "品质问题（坏果包赔）",
            "WRONG_ITEM", "发错货",
            "MISSING_ITEM", "少件漏发",
            "OTHER", "其他"
    );

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final AfterSalesMapper afterSalesMapper;

    @Operation(summary = "我的订单列表（最近5条）")
    @PostMapping("/order/list")
    public BaseResponse<List<AiOrderVO>> orderList(@RequestBody AiInternalQueryRequest req) {
        Long userId = requireUserId(req);
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(req.getStatus() != null, Order::getStatus, req.getStatus())
                .orderByDesc(Order::getCreateTime)
                .last("LIMIT 5"));

        // 批量查商品数，避免 N+1
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Long> itemCountMap = orderIds.isEmpty() ? Map.of()
                : orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.counting()));

        List<AiOrderVO> result = orders.stream().map(o -> {
            AiOrderVO vo = new AiOrderVO();
            vo.setOrderNo(o.getOrderNo());
            vo.setStatus(o.getStatus());
            vo.setStatusText(OrderStatusEnum.textOf(o.getStatus()));
            vo.setPayAmount(o.getPayAmount());
            vo.setItemCount(itemCountMap.getOrDefault(o.getId(), 0L).intValue());
            vo.setCreateTime(o.getCreateTime());
            return vo;
        }).toList();
        return ResultUtils.success(result);
    }

    @Operation(summary = "订单详情 + 物流信息（按 orderNo，校验归属）")
    @PostMapping("/order/detail")
    public BaseResponse<AiOrderDetailVO> orderDetail(@RequestBody AiInternalQueryRequest req) {
        Long userId = requireUserId(req);
        ThrowUtils.throwIf(req.getOrderNo() == null || req.getOrderNo().isBlank(),
                ErrorCode.PARAMS_ERROR, "orderNo 不能为空");

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, req.getOrderNo())
                .eq(Order::getUserId, userId));  // 越权防护：必须归属本人
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));

        AiOrderDetailVO vo = new AiOrderDetailVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderStatusEnum.textOf(order.getStatus()));
        vo.setTotalAmount(order.getTotalAmount());
        vo.setFreight(order.getFreight());
        vo.setDiscount(order.getDiscount());
        vo.setPayAmount(order.getPayAmount());
        vo.setItemCount(items.size());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setDeliveryTime(order.getDeliveryTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setLogType(order.getLogType());
        vo.setLogNo(order.getLogNo());
        vo.setRemark(order.getRemark());
        vo.setCancelReason(order.getCancelReason());
        vo.setItems(items.stream().map(it -> {
            AiOrderDetailVO.Item item = new AiOrderDetailVO.Item();
            item.setProductName(it.getProductName());
            item.setSpecName(it.getSpecName());
            item.setQuantity(it.getQuantity());
            item.setPrice(it.getPrice());
            item.setSubtotal(it.getSubtotal());
            return item;
        }).toList());
        return ResultUtils.success(vo);
    }

    @Operation(summary = "我的售后列表（最近5条）")
    @PostMapping("/after-sales/list")
    public BaseResponse<List<AiAfterSalesVO>> afterSalesList(@RequestBody AiInternalQueryRequest req) {
        Long userId = requireUserId(req);
        List<AfterSales> list = afterSalesMapper.selectList(new LambdaQueryWrapper<AfterSales>()
                .eq(AfterSales::getUserId, userId)
                .eq(req.getStatus() != null, AfterSales::getStatus, req.getStatus())
                .orderByDesc(AfterSales::getCreateTime)
                .last("LIMIT 5"));

        List<AiAfterSalesVO> result = new ArrayList<>(list.size());
        for (AfterSales a : list) {
            AiAfterSalesVO vo = new AiAfterSalesVO();
            vo.setAfterSalesNo(a.getAfterSalesNo());
            vo.setOrderNo(a.getOrderNo());
            vo.setType(a.getType());
            vo.setTypeText(AFTER_SALES_TYPE_TEXT.getOrDefault(a.getType(), "未知"));
            vo.setReasonType(a.getReasonType());
            vo.setReasonTypeText(AFTER_SALES_REASON_TEXT.getOrDefault(a.getReasonType(), a.getReasonType()));
            vo.setReason(a.getReason());
            vo.setRefundAmount(a.getRefundAmount());
            vo.setStatus(a.getStatus());
            vo.setStatusText(AFTER_SALES_STATUS_TEXT.getOrDefault(a.getStatus(), "未知"));
            vo.setHandleRemark(a.getHandleRemark());
            vo.setCreateTime(a.getCreateTime());
            vo.setHandleTime(a.getHandleTime());
            result.add(vo);
        }
        return ResultUtils.success(result);
    }

    private Long requireUserId(AiInternalQueryRequest req) {
        Long userId = req.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "userId 不能为空");
        return userId;
    }
}
