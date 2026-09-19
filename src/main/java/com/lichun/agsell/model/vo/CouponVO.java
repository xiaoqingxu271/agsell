package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板 VO（管理端列表/详情）
 */
@Data
public class CouponVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 券名称 */
    private String couponName;

    /** 券类型 1=满减券 2=折扣券 3=品类满减券 */
    private Integer couponType;

    /** 使用门槛（0=无门槛） */
    private BigDecimal threshold;

    /** 优惠金额（满减/品类券面额） */
    private BigDecimal amount;

    /** 折扣率（仅折扣券） */
    private BigDecimal discount;

    /** 折扣封顶金额（仅折扣券） */
    private BigDecimal maxDiscount;

    /** 适用商品ID列表（仅品类券） */
    private java.util.List<Long> productIds;

    /** 发放总量（0=不限量） */
    private Integer totalCount;

    /** 已领取数量 */
    private Integer receivedCount;

    /** 每人限领数量 */
    private Integer perUserLimit;

    /** 领取后有效天数 */
    private Integer validDays;

    /** 领取开始时间 */
    private LocalDateTime startTime;

    /** 领取结束时间 */
    private LocalDateTime endTime;

    /** 状态 0=下架 1=上架 */
    private Integer status;

    /** 排序值 */
    private Integer sort;

    private LocalDateTime createTime;
}
