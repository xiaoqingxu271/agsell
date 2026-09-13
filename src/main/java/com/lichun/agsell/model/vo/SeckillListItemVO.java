package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动列表项 VO（用户端）
 */
@Data
public class SeckillListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 活动编号（对外暴露的随机标识，替代自增ID防爬取） */
    private String activityCode;

    /** 商品ID */
    private Long productId;

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

    /** 剩余库存 */
    private Integer remainingStock;

    /** 每人限购数量 */
    private Integer seckillLimit;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 活动状态 1=未开始 2=进行中 3=已结束 */
    private Integer activityStatus;

    /** 活动状态文本 */
    private String activityStatusText;

    /** 倒计时秒数（未开始=距开始，进行中=距结束，已结束=0） */
    private Long countdownSeconds;

    /** 库存售出进度 0~100（remaining/stock） */
    private Integer progress;
}
