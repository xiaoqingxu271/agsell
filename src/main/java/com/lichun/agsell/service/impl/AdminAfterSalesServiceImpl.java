package com.lichun.agsell.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.AfterSalesMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.AdminAfterSalesHandleRequest;
import com.lichun.agsell.model.entity.AfterSales;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.vo.AdminAfterSalesDetailVO;
import com.lichun.agsell.model.vo.AdminAfterSalesListItemVO;
import com.lichun.agsell.model.vo.OrderDetailVO;
import com.lichun.agsell.service.AdminAfterSalesService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理端售后 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAfterSalesServiceImpl implements AdminAfterSalesService {

    /** 售后单状态：待处理 */
    private static final int STATUS_PENDING = 0;
    /** 售后单状态：已同意（退款完成） */
    private static final int STATUS_AGREED = 1;
    /** 售后单状态：已拒绝 */
    private static final int STATUS_REJECTED = 2;

    private static final Map<Integer, String> TYPE_TEXT_MAP = Map.of(
            1, "仅退款",
            2, "退货退款"
    );

    private static final Map<String, String> REASON_TYPE_TEXT_MAP = Map.of(
            "QUALITY", "品质问题（坏果包赔）",
            "WRONG_ITEM", "发错货",
            "MISSING_ITEM", "少件漏发",
            "OTHER", "其他原因"
    );

    private static final Map<Integer, String> STATUS_TEXT_MAP = Map.of(
            0, "待处理",
            1, "已同意",
            2, "已拒绝",
            3, "已撤销"
    );

    private static final Map<Integer, String> ORDER_STATUS_TEXT_MAP = Map.of(
            2, "待收货",
            3, "已完成"
    );

    private final AfterSalesMapper afterSalesMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SysUserMapper userMapper;
    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;

    @Override
    public Page<AdminAfterSalesListItemVO> list(int pageNum, int pageSize, Integer status,
                                                String afterSalesNo, String username) {
        Long adminId = AdminContext.getCurrentAdminId();
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);

        LambdaQueryWrapper<AfterSales> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(AfterSales::getStatus, status);
        }
        if (afterSalesNo != null && !afterSalesNo.isBlank()) {
            wrapper.like(AfterSales::getAfterSalesNo, afterSalesNo);
        }
        if (username != null && !username.isBlank()) {
            // 先按用户名查用户ID
            List<SysUser> users = userMapper.selectList(
                    new LambdaQueryWrapper<SysUser>()
                            .like(SysUser::getUsername, username)
                            .select(SysUser::getId));
            if (users.isEmpty()) {
                Page<AdminAfterSalesListItemVO> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(new ArrayList<>());
                return empty;
            }
            wrapper.in(AfterSales::getUserId,
                    users.stream().map(SysUser::getId).collect(Collectors.toList()));
        }
        wrapper.orderByDesc(AfterSales::getCreateTime);

        Page<AfterSales> page = afterSalesMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 批量查询用户信息，避免 N+1
        List<Long> userIds = page.getRecords().stream()
                .map(AfterSales::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        Page<AdminAfterSalesListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(afterSales -> {
            AdminAfterSalesListItemVO vo = new AdminAfterSalesListItemVO();
            vo.setId(afterSales.getId());
            vo.setAfterSalesNo(afterSales.getAfterSalesNo());
            vo.setOrderNo(afterSales.getOrderNo());
            vo.setUserId(afterSales.getUserId());
            SysUser user = userMap.get(afterSales.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
            }
            vo.setType(afterSales.getType());
            vo.setTypeText(TYPE_TEXT_MAP.getOrDefault(afterSales.getType(), "未知"));
            vo.setReasonType(afterSales.getReasonType());
            vo.setReasonTypeText(REASON_TYPE_TEXT_MAP.getOrDefault(afterSales.getReasonType(), "其他原因"));
            vo.setReason(afterSales.getReason());
            vo.setRefundAmount(afterSales.getRefundAmount());
            vo.setStatus(afterSales.getStatus());
            vo.setStatusText(STATUS_TEXT_MAP.getOrDefault(afterSales.getStatus(), "未知"));
            vo.setCreateTime(afterSales.getCreateTime());
            vo.setHandleTime(afterSales.getHandleTime());
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public AdminAfterSalesDetailVO getDetail(String afterSalesNo) {
        Long adminId = AdminContext.getCurrentAdminId();
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(afterSalesNo == null || afterSalesNo.isBlank(),
                ErrorCode.PARAMS_ERROR, "售后单号不能为空");

        AfterSales afterSales = afterSalesMapper.selectOne(new LambdaQueryWrapper<AfterSales>()
                .eq(AfterSales::getAfterSalesNo, afterSalesNo));
        ThrowUtils.throwIf(afterSales == null, ErrorCode.NOT_FOUND_ERROR, "售后单不存在");

        AdminAfterSalesDetailVO vo = new AdminAfterSalesDetailVO();
        vo.setId(afterSales.getId());
        vo.setAfterSalesNo(afterSales.getAfterSalesNo());
        vo.setOrderNo(afterSales.getOrderNo());
        vo.setUserId(afterSales.getUserId());
        vo.setOriginalStatus(afterSales.getOriginalStatus());
        vo.setOriginalStatusText(ORDER_STATUS_TEXT_MAP.getOrDefault(afterSales.getOriginalStatus(), "未知"));
        vo.setType(afterSales.getType());
        vo.setTypeText(TYPE_TEXT_MAP.getOrDefault(afterSales.getType(), "未知"));
        vo.setReasonType(afterSales.getReasonType());
        vo.setReasonTypeText(REASON_TYPE_TEXT_MAP.getOrDefault(afterSales.getReasonType(), "其他原因"));
        vo.setReason(afterSales.getReason());
        if (afterSales.getImages() != null && !afterSales.getImages().isEmpty()) {
            try {
                vo.setImages(JSONUtil.toList(afterSales.getImages(), String.class));
            } catch (Exception e) {
                vo.setImages(List.of());
            }
        } else {
            vo.setImages(List.of());
        }
        vo.setRefundAmount(afterSales.getRefundAmount());
        vo.setStatus(afterSales.getStatus());
        vo.setStatusText(STATUS_TEXT_MAP.getOrDefault(afterSales.getStatus(), "未知"));
        vo.setHandleRemark(afterSales.getHandleRemark());
        vo.setHandleBy(afterSales.getHandleBy());
        vo.setHandleTime(afterSales.getHandleTime());
        vo.setCreateTime(afterSales.getCreateTime());

        // 用户信息
        SysUser user = userMapper.selectById(afterSales.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
        }

        // 订单信息（实付金额 + 收货信息 + 商品明细）
        Order order = orderMapper.selectById(afterSales.getOrderId());
        if (order != null) {
            vo.setOrderPayAmount(order.getPayAmount());
            vo.setReceiver(order.getReceiver());
            vo.setPhone(order.getPhone());
            vo.setAddress(order.getAddress());
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
        }

        return vo;
    }

    @Override
    @Transactional
    public void handle(String afterSalesNo, AdminAfterSalesHandleRequest request) {
        Long adminId = AdminContext.getCurrentAdminId();
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request == null || request.getAgree() == null,
                ErrorCode.PARAMS_ERROR, "请选择处理结果");
        ThrowUtils.throwIf(afterSalesNo == null || afterSalesNo.isBlank(),
                ErrorCode.PARAMS_ERROR, "售后单号不能为空");
        ThrowUtils.throwIf(request.getRemark() != null && request.getRemark().length() > 500,
                ErrorCode.PARAMS_ERROR, "处理意见不能超过500字");

        AfterSales afterSales = afterSalesMapper.selectOne(new LambdaQueryWrapper<AfterSales>()
                .eq(AfterSales::getAfterSalesNo, afterSalesNo));
        ThrowUtils.throwIf(afterSales == null, ErrorCode.NOT_FOUND_ERROR, "售后单不存在");
        ThrowUtils.throwIf(afterSales.getStatus() != STATUS_PENDING,
                ErrorCode.AFTER_SALES_STATUS_ERROR, "该售后单已处理，请勿重复操作");

        Order order = orderMapper.selectById(afterSales.getOrderId());
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");

        if (Boolean.TRUE.equals(request.getAgree())) {
            // ===== 同意退款：订单置已退款 + 退货退款回补库存 + 已完成订单回滚销量 =====
            // 1. 回补库存：仅"退货退款(type=2)"回补——货物退回商家才可重新上架；
            //    "仅退款(type=1)"货未退回、仍在买家手里，库存不得回补
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
            boolean returnGoods = afterSales.getType() != null && afterSales.getType() == 2;
            for (OrderItem item : items) {
                if (returnGoods) {
                    if (item.getSpecId() != null) {
                        productSpecMapper.update(null, new LambdaUpdateWrapper<com.lichun.agsell.model.entity.ProductSpec>()
                                .setSql("stock = stock + " + item.getQuantity())
                                .eq(com.lichun.agsell.model.entity.ProductSpec::getId, item.getSpecId()));
                    } else {
                        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                                .setSql("stock = stock + " + item.getQuantity())
                                .eq(Product::getId, item.getProductId()));
                    }
                }
                // 仅已完成订单（确认收货累加过销量）回滚销量，GREATEST 防负数
                // 退款即交易未完成，仅退款/退货退款均回滚销量
                if (afterSales.getOriginalStatus() == 3) {
                    productMapper.update(null, new LambdaUpdateWrapper<Product>()
                            .setSql("sales = GREATEST(sales - " + item.getQuantity() + ", 0)")
                            .eq(Product::getId, item.getProductId()));
                }
            }

            // 2. 订单置为已退款（独立状态，不复用"已取消"；已取消保留给用户取消/超时取消）
            Order update = new Order();
            update.setId(order.getId());
            update.setStatus(6);
            update.setCancelReason("售后同意退款，订单已退款");
            orderMapper.updateById(update);
            log.info("[AfterSales] 管理员 {} 同意售后 {} 退款 {} 元, 订单 {} 置为已退款{}",
                    adminId, afterSalesNo, afterSales.getRefundAmount(), order.getOrderNo(),
                    returnGoods ? ", 库存已回补" : "（仅退款，库存不回补）");
        } else {
            // ===== 拒绝退款：订单恢复申请前状态 =====
            Order update = new Order();
            update.setId(order.getId());
            update.setStatus(afterSales.getOriginalStatus());
            update.setCancelReason(null);
            orderMapper.updateById(update);
            log.info("[AfterSales] 管理员 {} 拒绝售后 {}，订单 {} 恢复状态 {}",
                    adminId, afterSalesNo, order.getOrderNo(), afterSales.getOriginalStatus());
        }

        // 3. 更新售后单处理结果
        AfterSales update = new AfterSales();
        update.setId(afterSales.getId());
        update.setStatus(Boolean.TRUE.equals(request.getAgree()) ? STATUS_AGREED : STATUS_REJECTED);
        update.setHandleRemark(request.getRemark());
        update.setHandleBy(adminId);
        update.setHandleTime(LocalDateTime.now());
        afterSalesMapper.updateById(update);
    }
}
