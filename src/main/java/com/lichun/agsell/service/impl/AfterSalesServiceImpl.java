package com.lichun.agsell.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.AfterSalesMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.model.dto.AfterSalesCreateRequest;
import com.lichun.agsell.model.entity.AfterSales;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.enums.OrderStatusEnum;
import com.lichun.agsell.model.vo.AfterSalesDetailVO;
import com.lichun.agsell.model.vo.AfterSalesListItemVO;
import com.lichun.agsell.service.AfterSalesService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户端售后 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSalesServiceImpl implements AfterSalesService {

    /** 售后单状态：待处理 */
    private static final int STATUS_PENDING = 0;
    /** 售后单状态：已同意（退款完成） */
    private static final int STATUS_AGREED = 1;
    /** 售后单状态：已拒绝 */
    private static final int STATUS_REJECTED = 2;
    /** 售后单状态：已撤销 */
    private static final int STATUS_CANCELLED = 3;

    /** 可申请售后的订单状态：待收货 / 已完成 */
    private static final List<Integer> APPLICABLE_ORDER_STATUS = List.of(
            OrderStatusEnum.PENDING_RECEIPT.getCode(),
            OrderStatusEnum.COMPLETED.getCode());

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

    private final AfterSalesMapper afterSalesMapper;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public AfterSalesDetailVO apply(AfterSalesCreateRequest request) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        ThrowUtils.throwIf(request.getOrderNo() == null || request.getOrderNo().isBlank(),
                ErrorCode.PARAMS_ERROR, "订单号不能为空");
        ThrowUtils.throwIf(request.getType() == null || !TYPE_TEXT_MAP.containsKey(request.getType()),
                ErrorCode.PARAMS_ERROR, "售后类型无效");
        ThrowUtils.throwIf(request.getReasonType() == null || !REASON_TYPE_TEXT_MAP.containsKey(request.getReasonType()),
                ErrorCode.PARAMS_ERROR, "售后原因类型无效");
        ThrowUtils.throwIf(request.getReason() != null && request.getReason().length() > 500,
                ErrorCode.PARAMS_ERROR, "问题描述不能超过500字");

        // 1. 校验订单归属
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, request.getOrderNo())
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");

        // 2. 校验同一订单不存在进行中的售后单（防重复申请，优先于状态校验）
        long pendingCount = afterSalesMapper.selectCount(new LambdaQueryWrapper<AfterSales>()
                .eq(AfterSales::getOrderId, order.getId())
                .eq(AfterSales::getStatus, STATUS_PENDING));
        ThrowUtils.throwIf(pendingCount > 0, ErrorCode.OPERATION_ERROR, "该订单已有进行中的售后申请");

        // 3. 校验订单状态（仅待收货/已完成可申请）
        ThrowUtils.throwIf(!APPLICABLE_ORDER_STATUS.contains(order.getStatus()),
                ErrorCode.ORDER_STATUS_ERROR, "仅待收货/已完成的订单可以申请售后");

        // 4. 校验退款金额：必须大于0且不超过订单实付金额
        BigDecimal refundAmount = request.getRefundAmount() != null
                ? request.getRefundAmount() : order.getPayAmount();
        ThrowUtils.throwIf(refundAmount.compareTo(BigDecimal.ZERO) <= 0,
                ErrorCode.PARAMS_ERROR, "退款金额必须大于0");
        ThrowUtils.throwIf(refundAmount.compareTo(order.getPayAmount()) > 0,
                ErrorCode.PARAMS_ERROR, "退款金额不能超过订单实付金额");

        // 5. 创建售后单
        AfterSales afterSales = new AfterSales();
        afterSales.setAfterSalesNo(generateAfterSalesNo());
        afterSales.setOrderId(order.getId());
        afterSales.setOrderNo(order.getOrderNo());
        afterSales.setUserId(userId);
        afterSales.setOriginalStatus(order.getStatus());
        afterSales.setType(request.getType());
        afterSales.setReasonType(request.getReasonType());
        afterSales.setReason(request.getReason());
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            afterSales.setImages(JSONUtil.toJsonStr(request.getImages()));
        }
        afterSales.setRefundAmount(refundAmount);
        afterSales.setStatus(STATUS_PENDING);
        afterSalesMapper.insert(afterSales);
        log.info("[AfterSales] 用户 {} 申请售后成功, 售后单号: {}, 订单号: {}, 退款金额: {}",
                userId, afterSales.getAfterSalesNo(), order.getOrderNo(), refundAmount);

        // 5. 订单进入售后中
        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(OrderStatusEnum.AFTER_SALES.getCode()); // 售后中
        orderMapper.updateById(update);

        return convertToDetailVO(afterSales);
    }

    @Override
    public Page<AfterSalesListItemVO> listMine(int pageNum, int pageSize, Integer status) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        LambdaQueryWrapper<AfterSales> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AfterSales::getUserId, userId);
        if (status != null) {
            wrapper.eq(AfterSales::getStatus, status);
        }
        wrapper.orderByDesc(AfterSales::getCreateTime);

        Page<AfterSales> page = afterSalesMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<AfterSalesListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(this::convertToListItemVO)
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public AfterSalesDetailVO getDetail(String afterSalesNo) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(afterSalesNo == null || afterSalesNo.isBlank(),
                ErrorCode.PARAMS_ERROR, "售后单号不能为空");

        AfterSales afterSales = afterSalesMapper.selectOne(new LambdaQueryWrapper<AfterSales>()
                .eq(AfterSales::getAfterSalesNo, afterSalesNo)
                .eq(AfterSales::getUserId, userId));
        ThrowUtils.throwIf(afterSales == null, ErrorCode.NOT_FOUND_ERROR, "售后单不存在");

        return convertToDetailVO(afterSales);
    }

    @Override
    @Transactional
    public void cancelApply(String afterSalesNo) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(afterSalesNo == null || afterSalesNo.isBlank(),
                ErrorCode.PARAMS_ERROR, "售后单号不能为空");

        AfterSales afterSales = afterSalesMapper.selectOne(new LambdaQueryWrapper<AfterSales>()
                .eq(AfterSales::getAfterSalesNo, afterSalesNo)
                .eq(AfterSales::getUserId, userId));
        ThrowUtils.throwIf(afterSales == null, ErrorCode.NOT_FOUND_ERROR, "售后单不存在");
        ThrowUtils.throwIf(afterSales.getStatus() != STATUS_PENDING,
                ErrorCode.AFTER_SALES_STATUS_ERROR, "只有待处理的售后单可以撤销");

        // 1. 售后单置为已撤销
        AfterSales update = new AfterSales();
        update.setId(afterSales.getId());
        update.setStatus(STATUS_CANCELLED);
        afterSalesMapper.updateById(update);

        // 2. 订单恢复申请前状态（防止状态已被其他流程修改，按 status=5 条件更新）
        orderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Order>()
                .eq("id", afterSales.getOrderId())
                .eq("status", OrderStatusEnum.AFTER_SALES.getCode())
                .set("status", afterSales.getOriginalStatus()));
        log.info("[AfterSales] 用户 {} 撤销售后申请, 售后单号: {}, 订单恢复状态: {}",
                userId, afterSalesNo, afterSales.getOriginalStatus());
    }

    // ==================== 私有方法 ====================

    /**
     * 生成售后单号：AFS + 雪花ID（全局唯一、并发安全）
     */
    private String generateAfterSalesNo() {
        return "AFS" + IdUtil.getSnowflakeNextIdStr();
    }

    private AfterSalesListItemVO convertToListItemVO(AfterSales afterSales) {
        AfterSalesListItemVO vo = new AfterSalesListItemVO();
        vo.setId(afterSales.getId());
        vo.setAfterSalesNo(afterSales.getAfterSalesNo());
        vo.setOrderNo(afterSales.getOrderNo());
        vo.setType(afterSales.getType());
        vo.setTypeText(TYPE_TEXT_MAP.getOrDefault(afterSales.getType(), "未知"));
        vo.setReasonType(afterSales.getReasonType());
        vo.setReasonTypeText(REASON_TYPE_TEXT_MAP.getOrDefault(afterSales.getReasonType(), "其他原因"));
        vo.setRefundAmount(afterSales.getRefundAmount());
        vo.setStatus(afterSales.getStatus());
        vo.setStatusText(STATUS_TEXT_MAP.getOrDefault(afterSales.getStatus(), "未知"));
        vo.setReason(afterSales.getReason());
        vo.setHandleRemark(afterSales.getHandleRemark());
        vo.setCreateTime(afterSales.getCreateTime());
        vo.setHandleTime(afterSales.getHandleTime());
        return vo;
    }

    private AfterSalesDetailVO convertToDetailVO(AfterSales afterSales) {
        AfterSalesDetailVO vo = new AfterSalesDetailVO();
        vo.setId(afterSales.getId());
        vo.setAfterSalesNo(afterSales.getAfterSalesNo());
        vo.setOrderNo(afterSales.getOrderNo());
        vo.setOriginalStatus(afterSales.getOriginalStatus());
        vo.setOriginalStatusText(OrderStatusEnum.textOf(afterSales.getOriginalStatus()));
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
        vo.setHandleTime(afterSales.getHandleTime());
        vo.setCreateTime(afterSales.getCreateTime());
        return vo;
    }
}
