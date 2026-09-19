package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体
 * 对应数据库表 coupon
 */
@Data
@TableName("coupon")
public class Coupon implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

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

    /** 折扣封顶金额（仅折扣券，0/NULL=不封顶） */
    private BigDecimal maxDiscount;

    /** 发放总量（0=不限量） */
    private Integer totalCount;

    /** 已领取数量（冗余计数，Redis 预热数据源） */
    private Integer receivedCount;

    /** 每人限领数量（本期固定1） */
    private Integer perUserLimit;

    /** 领取后有效天数（相对有效期，从领取时刻起算） */
    private Integer validDays;

    /** 领取开始时间（NULL=立即开始） */
    private LocalDateTime startTime;

    /** 领取结束时间（NULL=长期有效） */
    private LocalDateTime endTime;

    /** 状态 0=下架 1=上架 */
    private Integer status;

    /** 排序值，越大越靠前 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
