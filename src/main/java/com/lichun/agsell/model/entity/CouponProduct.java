package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品类券适用商品关联实体
 * 对应数据库表 coupon_product（coupon_type=3 的券才有关联记录）
 */
@Data
@TableName("coupon_product")
public class CouponProduct implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 券模板ID */
    private Long couponId;

    /** 商品ID */
    private Long productId;

    private LocalDateTime createTime;
}
