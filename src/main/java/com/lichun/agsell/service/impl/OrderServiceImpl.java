package com.lichun.agsell.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.CartMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.mapper.SysUserAddressMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.OrderCreateRequest;
import com.lichun.agsell.model.entity.*;
import com.lichun.agsell.model.vo.OrderCreateVO;
import com.lichun.agsell.model.vo.OrderDetailVO;
import com.lichun.agsell.model.vo.OrderListItemVO;
import com.lichun.agsell.service.OrderService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Map<String, String> STATUS_TEXT_MAP = Map.of(
            "0", "待付款",
            "1", "待发货",
            "2", "待收货",
            "3", "已完成",
            "4", "已取消",
            "5", "售后中"
    );

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;
    private final SysUserAddressMapper addressMapper;
    private final SysUserMapper userMapper;

    @Override
    @Transactional
    public OrderCreateVO createOrder(OrderCreateRequest request) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request.getAddressId() == null, ErrorCode.PARAMS_ERROR, "请选择收货地址");

        // 校验：必须有 cartItemIds（购物车结算）或 orderItems（立即购买）之一
        boolean hasCartIds = request.getCartItemIds() != null && !request.getCartItemIds().isEmpty();
        boolean hasOrderItems = request.getOrderItems() != null && !request.getOrderItems().isEmpty();
        ThrowUtils.throwIf(!hasCartIds && !hasOrderItems,
                ErrorCode.PARAMS_ERROR, "请选择要结算的商品");

        // 1. 查询地址并校验归属（防止使用他人地址ID下单）
        SysUserAddress address = addressMapper.selectById(request.getAddressId());
        ThrowUtils.throwIf(address == null, ErrorCode.PARAMS_ERROR, "收货地址不存在");
        ThrowUtils.throwIf(!Objects.equals(address.getUserId(), userId),
                ErrorCode.NO_AUTH_ERROR, "无权使用该收货地址");

        // 2. 构建订单明细（所有金额/名称/图片均以服务端数据库为准，不信任前端）
        List<OrderItem> orderItems = new ArrayList<>();

        if (hasOrderItems) {
            // 立即购买流程：仅信任 productId / specId / quantity，服务端重新计价
            for (OrderCreateRequest.OrderItemDTO itemDto : request.getOrderItems()) {
                ThrowUtils.throwIf(itemDto.getProductId() == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
                ThrowUtils.throwIf(itemDto.getQuantity() == null || itemDto.getQuantity() <= 0,
                        ErrorCode.PARAMS_ERROR, "商品数量无效");

                Product product = productMapper.selectById(itemDto.getProductId());
                ThrowUtils.throwIf(product == null || product.getStatus() != 1,
                        ErrorCode.NOT_FOUND_ERROR, "商品已下架或不存在");

                // 规格校验：若传了规格ID，必须是该商品下的有效规格
                ProductSpec spec = null;
                if (itemDto.getSpecId() != null) {
                    spec = productSpecMapper.selectById(itemDto.getSpecId());
                    ThrowUtils.throwIf(spec == null || !Objects.equals(spec.getProductId(), product.getId()),
                            ErrorCode.PARAMS_ERROR, "商品规格不存在");
                }

                // 库存校验（下单时快速失败，真正扣减在支付成功时）
                int availableStock = spec != null ? spec.getStock() : product.getStock();
                ThrowUtils.throwIf(itemDto.getQuantity() > availableStock,
                        ErrorCode.STOCK_INSUFFICIENT, String.format("商品 %s 库存不足", product.getName()));

                // 服务端重新取值，忽略前端传入的价格/名称/图片快照
                BigDecimal price = spec != null ? spec.getPrice() : product.getPrice();
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(itemDto.getQuantity()));

                OrderItem item = new OrderItem();
                item.setProductId(product.getId());
                item.setSpecId(spec != null ? spec.getId() : null);
                item.setProductName(product.getName());
                item.setProductImage(product.getMainImage());
                item.setSpecName(spec != null ? spec.getSpecName() : null);
                item.setPrice(price);
                item.setQuantity(itemDto.getQuantity());
                item.setSubtotal(subtotal);
                orderItems.add(item);
            }
        } else {
            // 购物车结算流程（同样以数据库价格为准）
            List<Cart> cartItems = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                    .eq(Cart::getUserId, userId)
                    .eq(Cart::getSelected, 1)
                    .in(Cart::getId, request.getCartItemIds()));
            ThrowUtils.throwIf(cartItems.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要结算的商品");

            for (Cart cartItem : cartItems) {
                Product product = productMapper.selectById(cartItem.getProductId());
                ThrowUtils.throwIf(product == null || product.getStatus() != 1,
                        ErrorCode.NOT_FOUND_ERROR, "商品已下架或不存在: " + cartItem.getProductId());

                ProductSpec spec = cartItem.getSpecId() != null
                        ? productSpecMapper.selectById(cartItem.getSpecId()) : null;
                if (cartItem.getSpecId() != null && spec == null) {
                    ThrowUtils.throwIf(true, ErrorCode.NOT_FOUND_ERROR, "规格不存在");
                }

                // 校验库存
                int availableStock = spec != null ? spec.getStock() : product.getStock();
                ThrowUtils.throwIf(cartItem.getQuantity() > availableStock,
                        ErrorCode.STOCK_INSUFFICIENT, String.format("商品 %s 库存不足", product.getName()));

                BigDecimal price = spec != null ? spec.getPrice() : product.getPrice();
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(cartItem.getQuantity()));

                OrderItem item = new OrderItem();
                item.setProductId(product.getId());
                item.setSpecId(spec != null ? spec.getId() : null);
                item.setProductName(product.getName());
                item.setProductImage(product.getMainImage());
                item.setSpecName(spec != null ? spec.getSpecName() : null);
                item.setPrice(price);
                item.setQuantity(cartItem.getQuantity());
                item.setSubtotal(subtotal);
                orderItems.add(item);
            }
        }

        // 3. 计算总金额
        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. 生成订单
        String orderNo = generateOrderNo();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setFreight(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setPayAmount(totalAmount);
        order.setStatus(0); // 待付款
        order.setAddressId(request.getAddressId());
        order.setReceiver(address.getReceiver());
        order.setPhone(address.getPhone());
        order.setAddress(address.getProvince() + address.getCity()
                + address.getDistrict() + address.getDetail());
        order.setRemark(request.getRemark());
        orderMapper.insert(order);

        // 5. 保存订单明细
        orderItems.forEach(item -> item.setOrderId(order.getId()));
        orderItemMapper.insert(orderItems);

        // 6. 购物车结算：删除已下单的购物车条目
        if (hasCartIds) {
            cartMapper.delete(new LambdaQueryWrapper<Cart>()
                    .eq(Cart::getUserId, userId)
                    .in(Cart::getId, request.getCartItemIds()));
        }

        // 7. 返回订单信息
        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderNo(orderNo);
        vo.setPayAmount(totalAmount);
        vo.setTotalAmount(totalAmount);
        vo.setStatus(0);
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    @Override
    public Page<OrderListItemVO> listOrders(int pageNum, int pageSize, Integer status) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<OrderListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(order -> {
            OrderListItemVO vo = new OrderListItemVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setPayAmount(order.getPayAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusText(STATUS_TEXT_MAP.get(String.valueOf(order.getStatus())));
            vo.setCreateTime(order.getCreateTime());
            // 查询商品数量
            long itemCount = orderItemMapper.selectCount(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
            vo.setItemCount((int) itemCount);
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public OrderDetailVO getOrderDetail(String orderNo) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setFreight(order.getFreight());
        vo.setDiscount(order.getDiscount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(STATUS_TEXT_MAP.get(String.valueOf(order.getStatus())));
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

        // 查询订单明细
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        vo.setItems(items.stream().map(item -> {
            OrderDetailVO.OrderItemVO itemVo = new OrderDetailVO.OrderItemVO();
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
    public void cancelOrder(String orderNo, String reason) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        ThrowUtils.throwIf(order.getStatus() != 0,
                ErrorCode.ORDER_STATUS_ERROR, "只有待付款订单可以取消");

        // 库存说明：库存仅在支付成功时扣减，待付款订单未占用库存，取消时无需恢复库存。
        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(4); // 已取消
        update.setCancelReason(reason);
        orderMapper.updateById(update);
    }

    @Override
    @Transactional
    public void confirmReceive(String orderNo) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        ThrowUtils.throwIf(order.getStatus() != 2,
                ErrorCode.ORDER_STATUS_ERROR, "只有待收货订单可以确认收货");

        // 更新订单状态
        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(3); // 已完成
        update.setReceiveTime(LocalDateTime.now());
        orderMapper.updateById(update);

        // 增加销量（库存已在支付成功时扣减，这里不再重复扣减）
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .setSql("sales = sales + " + item.getQuantity())
                    .eq(Product::getId, item.getProductId()));
        }
    }

    @Override
    @Transactional
    public int cancelExpiredOrders(int expireMinutes) {
        ThrowUtils.throwIf(expireMinutes <= 0, ErrorCode.PARAMS_ERROR, "超时时长必须大于0");
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(expireMinutes);

        List<Order> expired = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 0) // 仅待付款
                .lt(Order::getCreateTime, deadline));
        if (expired.isEmpty()) {
            return 0;
        }
        for (Order order : expired) {
            Order update = new Order();
            update.setId(order.getId());
            update.setStatus(4); // 已取消
            update.setCancelReason("订单超时未支付，系统自动取消");
            orderMapper.updateById(update);
        }
        return expired.size();
    }

    // ==================== 私有方法 ====================

    /**
     * 生成订单号：AGS + 雪花ID（全局唯一、并发安全），长度 22 位，满足 order_no VARCHAR(32)
     */
    private String generateOrderNo() {
        return "AGS" + IdUtil.getSnowflakeNextIdStr();
    }
}
