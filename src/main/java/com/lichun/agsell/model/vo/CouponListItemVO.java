package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 领券中心券模板 VO（用户端）
 */
@Data
public class CouponListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 券名称 */
    private String couponName;

    /** 券类型 1=满减券 */
    private Integer couponType;

    /** 使用门槛（0=无门槛） */
    private BigDecimal threshold;

    /** 优惠金额 */
    private BigDecimal amount;

    /** 发放总量（0=不限量） */
    private Integer totalCount;

    /** 已领取数量 */
    private Integer receivedCount;

    /** 折扣率（折扣券） */
    private BigDecimal discount;

    /** 领取后有效天数 */
    private Integer validDays;

    /** 领取开始时间 */
    private LocalDateTime startTime;

    /** 领取结束时间 */
    private LocalDateTime endTime;

    /** 当前用户是否已领取 */
    private Boolean received;
}
