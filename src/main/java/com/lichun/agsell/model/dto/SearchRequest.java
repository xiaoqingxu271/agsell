package com.lichun.agsell.model.dto;

import lombok.Data;

/**
 * 商品搜索请求
 */
@Data
public class SearchRequest {

    /** 搜索关键词 */
    private String keyword;

    /** 分类过滤（可选） */
    private Long categoryId;

    /** 排序：空(相关度)/hot/new/price_asc/price_desc */
    private String sortBy;

    /** 页码，默认 1 */
    private int pageNum = 1;

    /** 每页数量，默认 12 */
    private int pageSize = 12;
}
