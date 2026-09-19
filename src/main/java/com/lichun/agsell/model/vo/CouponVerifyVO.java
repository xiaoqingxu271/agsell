package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 优惠券核销结果（服务内部流转：OrderServiceImpl 落账用）
 */
@Data
public class CouponVerifyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户券ID（未用券为 null） */
    private Long userCouponId;

    /** 优惠金额（= min(面额, 订单金额)；未用券为 0） */
    private BigDecimal discount;

    /** 优惠券名称（快照，落 order.coupon_name） */
    private String couponName;
}
