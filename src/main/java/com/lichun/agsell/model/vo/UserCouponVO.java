package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券 VO（我的券包，含券模板冗余信息）
 */
@Data
public class UserCouponVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户券ID（核销/下单时作为 couponId 传入） */
    private Long id;

    /** 券模板ID */
    private Long couponId;

    /** 券名称 */
    private String couponName;

    /** 券类型 1=满减券 2=折扣券 3=品类券 */
    private Integer couponType;

    /** 使用门槛（0=无门槛） */
    private BigDecimal threshold;

    /** 优惠金额 */
    private BigDecimal amount;

    /** 折扣率（折扣券） */
    private BigDecimal discount;

    /** 折扣封顶（折扣券） */
    private BigDecimal maxDiscount;

    /** 状态 0=未使用 1=已使用 2=已过期 */
    private Integer status;

    /** 核销订单号 */
    private String orderNo;

    /** 核销时间 */
    private LocalDateTime useTime;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 领取时间 */
    private LocalDateTime receiveTime;
}
