package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动详情 VO（用户端）
 */
@Data
public class SeckillDetailVO implements Serializable {

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

    /** 商品产地 */
    private String origin;

    /** 保质期 */
    private String shelfLife;

    /** 储存方式 */
    private String storage;

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

    /** 倒计时秒数 */
    private Long countdownSeconds;

    /** 当前用户是否已参与过该秒杀 */
    private Boolean userSeckilled;

    /** 库存售出进度 0~100 */
    private Integer progress;

    /** 该商品所有可秒杀规格列表 */
    private List<SeckillSpecVO> specs;

    @Data
    public static class SeckillSpecVO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 规格ID */
        private Long specId;

        /** 规格名称 */
        private String specName;

        /** 规格图片 */
        private String specImage;

        /** 该规格对应的秒杀活动编号 */
        private String activityCode;

        /** 秒杀价 */
        private BigDecimal seckillPrice;

        /** 秒杀总库存 */
        private Integer seckillStock;

        /** 剩余库存 */
        private Integer remainingStock;

        /** 每人限购 */
        private Integer seckillLimit;

        /** 活动状态 1=未开始 2=进行中 3=已结束 */
        private Integer activityStatus;

        /** 倒计时秒数 */
        private Long countdownSeconds;

        /** 库存售出进度 0~100 */
        private Integer progress;

        /** 是否已抢 */
        private Boolean userSeckilled;
    }
}
