package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户优惠券实体
 * 对应数据库表 user_coupon
 */
@Data
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 券模板ID */
    private Long couponId;

    /** 状态 0=未使用 1=已使用 2=已过期 */
    private Integer status;

    /** 核销订单号（已使用时记录，对账用） */
    private String orderNo;

    /** 核销时间 */
    private LocalDateTime useTime;

    /** 该张券的过期时间（= 领取时间 + valid_days） */
    private LocalDateTime expireTime;

    /** 领取时间 */
    private LocalDateTime receiveTime;
}
