package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品查询请求（用户端）
 */
@Data
public class ProductListRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类ID */
    private Long categoryId;

    /** 搜索关键词 */
    private String keyword;

    /** 排序方式: hot=销量, new=新品, price_asc=价格低到高, price_desc=价格高到低 */
    private String sortBy;

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页数量 */
    private Integer pageSize = 12;
}
