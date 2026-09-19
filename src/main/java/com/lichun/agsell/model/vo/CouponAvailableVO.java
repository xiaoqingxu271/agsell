package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算页可用优惠券 VO
 * usable / unusable 两组，unusable 附不可用原因供前端展示
 */
@Data
public class CouponAvailableVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 可用券列表 */
    private List<Item> usable;

    /** 不可用券列表（含原因） */
    private List<Item> unusable;

    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 用户券ID（下单时作为 couponId 传入） */
        private Long id;

        /** 券模板ID */
        private Long couponId;

        /** 券名称 */
        private String couponName;

        /** 券类型 1=满减 2=折扣 3=品类 */
        private Integer couponType;

        /** 使用门槛（0=无门槛） */
        private BigDecimal threshold;

        /** 优惠金额 */
        private BigDecimal amount;

        /** 折扣率（折扣券） */
        private BigDecimal discount;

        /** 折扣封顶（折扣券） */
        private BigDecimal maxDiscount;

        /** 过期时间 */
        private LocalDateTime expireTime;

        /** 状态 0=未使用 */
        private Integer status;

        /** 不可用原因（可用券为 null） */
        private String reason;
    }
}
