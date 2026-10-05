package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI 智能导购商品搜索请求（Python 客服服务回调）
 * <p>
 * 条件全部可选：不传 keyword 时按销量降序返回热销商品（导购降级路径）。
 */
@Data
public class AiProductSearchRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键词（匹配商品名称/副标题，可选） */
    private String keyword;

    /** 价格区间下限（可选） */
    private BigDecimal minPrice;

    /** 价格区间上限（可选） */
    private BigDecimal maxPrice;

    /** 返回条数（可选，默认 6，上限 10） */
    private Integer limit;
}
