package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.OrderShipRequest;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.enums.OrderStatusEnum;
import com.lichun.agsell.model.vo.AdminOrderDetailVO;
import com.lichun.agsell.model.vo.AdminOrderListItemVO;
import com.lichun.agsell.model.vo.OrderDetailVO;
import com.lichun.agsell.service.AdminOrderService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SysUserMapper userMapper;

    @Override
    public Page<AdminOrderListItemVO> listOrders(int pageNum, int pageSize, Integer status,
                                                  String orderNo, String username) {
        Long adminId = AdminContext.getCurrentAdminId();
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        if (orderNo != null && !orderNo.isBlank()) {
            wrapper.like(Order::getOrderNo, orderNo);
        }
        if (username != null && !username.isBlank()) {
            // 先根据用户名查询用户ID
            List<SysUser> users = userMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .like(SysUser::getUsername, username)
                            .select(SysUser::getId));
            if (users.isEmpty()) {
                Page<AdminOrderListItemVO> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(new ArrayList<>());
                return empty;
            }
            List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
            wrapper.in(Order::getUserId, userIds);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<Order> records = page.getRecords();

        // 批量查询用户信息，避免 N+1
        List<Long> userIds = records.stream()
                .map(Order::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        // 批量统计每单商品数量，避免 N+1
        List<Long> orderIds = records.stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, Long> itemCountMap = orderIds.isEmpty() ? Map.of()
                : orderItemMapper.selectList(
                                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds))
                        .stream()
                        .collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.counting()));

        Page<AdminOrderListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records.stream().map(order -> {
            AdminOrderListItemVO vo = new AdminOrderListItemVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setUserId(order.getUserId());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setPayAmount(order.getPayAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusText(OrderStatusEnum.textOf(order.getStatus()));
            vo.setReceiver(order.getReceiver());
            vo.setPhone(order.getPhone());
            vo.setCreateTime(order.getCreateTime());

            SysUser user = userMap.get(order.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
            }

            Long itemCount = itemCountMap.getOrDefault(order.getId(), 0L);
            vo.setItemCount(itemCount.intValue());
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public AdminOrderDetailVO getOrderDetail(String orderNo) {
        Long adminId = AdminContext.getCurrentAdminId();
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");

        AdminOrderDetailVO vo = new AdminOrderDetailVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setFreight(order.getFreight());
        vo.setDiscount(order.getDiscount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderStatusEnum.textOf(order.getStatus()));
        vo.setReceiver(order.getReceiver());
        vo.setPhone(order.getPhone());
        vo.setAddress(order.getAddress());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setDeliveryTime(order.getDeliveryTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setLogType(order.getLogType());
        vo.setLogNo(order.getLogNo());
        vo.setCancelReason(order.getCancelReason());

        // 查询用户信息
        SysUser user = userMapper.selectById(order.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
        }

        // 查询订单明细
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        vo.setItems(items.stream().map(item -> {
            OrderDetailVO.OrderItemVO itemVo = new OrderDetailVO.OrderItemVO();
            itemVo.setId(item.getId());
            itemVo.setProductId(item.getProductId());
            itemVo.setProductName(item.getProductName());
            itemVo.setProductImage(item.getProductImage());
            itemVo.setSpecName(item.getSpecName());
            itemVo.setPrice(item.getPrice());
            itemVo.setQuantity(item.getQuantity());
            itemVo.setSubtotal(item.getSubtotal());
            return itemVo;
        }).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional
    public void shipOrder(String orderNo, OrderShipRequest request) {
        Long adminId = AdminContext.getCurrentAdminId();
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        ThrowUtils.throwIf(request.getLogType() == null || request.getLogType().isBlank(),
                ErrorCode.PARAMS_ERROR, "物流公司不能为空");
        ThrowUtils.throwIf(request.getLogNo() == null || request.getLogNo().isBlank(),
                ErrorCode.PARAMS_ERROR, "物流单号不能为空");

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        ThrowUtils.throwIf(order.getStatus() != OrderStatusEnum.PENDING_SHIPMENT.getCode(),
                ErrorCode.ORDER_STATUS_ERROR, "只有待发货订单可以发货");

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(OrderStatusEnum.PENDING_RECEIPT.getCode()); // 待收货
        update.setLogType(request.getLogType());
        update.setLogNo(request.getLogNo());
        update.setDeliveryTime(java.time.LocalDateTime.now());
        orderMapper.updateById(update);
    }
}
