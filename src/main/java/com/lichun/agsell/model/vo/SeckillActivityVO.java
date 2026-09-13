package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动 VO（管理端列表/详情）
 */
@Data
public class SeckillActivityVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 活动编号（对外暴露的随机标识，替代自增ID防爬取） */
    private String activityCode;

    /** 商品ID */
    private Long productId;

    /** 绑定规格ID */
    private Long productSpecId;

    /** 商品名称 */
    private String productName;

    /** 商品主图 */
    private String productImage;

    /** 商品现价 */
    private BigDecimal productPrice;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 秒杀总库存 */
    private Integer seckillStock;

    /** 剩余库存（Redis 实时值，无缓存时为总量） */
    private Integer remainingStock;

    /** 每人限购数量 */
    private Integer seckillLimit;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态 0=下架 1=上架 */
    private Integer status;

    /** 活动状态文本（未开始/进行中/已结束/已下架） */
    private String activityStatusText;

    /** 排序值 */
    private Integer sort;

    private LocalDateTime createTime;
}
