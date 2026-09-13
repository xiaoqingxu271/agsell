package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动创建/编辑请求（管理端）
 */
@Data
public class SeckillActivityRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID */
    private Long productId;

    /** 绑定规格ID（可选） */
    private Long productSpecId;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 秒杀库存 */
    private Integer seckillStock;

    /** 每人限购数量 */
    private Integer seckillLimit;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 排序值 */
    private Integer sort;

    /** 状态 0=下架 1=上架 */
    private Integer status;
}
