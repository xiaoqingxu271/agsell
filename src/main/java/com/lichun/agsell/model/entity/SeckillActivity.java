package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体
 * 对应数据库表 seckill_activity
 */
@Data
@TableName("seckill_activity")
public class SeckillActivity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动编号（对外暴露的随机标识，不可枚举） */
    private String activityCode;

    /** 商品ID */
    private Long productId;

    /** 绑定规格ID（无规格为NULL，按商品维度秒杀） */
    private Long productSpecId;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 秒杀库存（活动放出的名额） */
    private Integer seckillStock;

    /** 每人限购数量 */
    private Integer seckillLimit;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
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
