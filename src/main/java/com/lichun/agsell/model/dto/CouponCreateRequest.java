package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板创建/编辑请求（管理端）
 */
@Data
public class CouponCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 券名称（如：满99减10） */
    private String couponName;

    /** 券类型 1=满减券 2=折扣券 3=品类满减券 */
    private Integer couponType;

    /** 使用门槛（订单满X元可用，0=无门槛） */
    private BigDecimal threshold;

    /** 优惠金额（满减/品类券面额；折扣券不用） */
    private BigDecimal amount;

    /** 折扣率（仅折扣券，0.85=85折，0~1开区间） */
    private BigDecimal discount;

    /** 折扣封顶金额（仅折扣券，0/空=不封顶） */
    private BigDecimal maxDiscount;

    /** 适用商品ID列表（仅品类券，至少1个） */
    private java.util.List<Long> productIds;

    /** 发放总量（0=不限量） */
    private Integer totalCount;

    /** 每人限领数量（本期固定1） */
    private Integer perUserLimit;

    /** 领取后有效天数（相对有效期，从领取时刻起算） */
    private Integer validDays;

    /** 领取开始时间（NULL=立即开始） */
    private LocalDateTime startTime;

    /** 领取结束时间（NULL=长期有效） */
    private LocalDateTime endTime;

    /** 排序值 */
    private Integer sort;

    /** 状态 0=下架 1=上架 */
    private Integer status;
}
