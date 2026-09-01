package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品查询请求（管理端）
 */
@Data
public class ProductQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品名称（模糊搜索） */
    private String name;

    /** 分类ID */
    private Long categoryId;

    /** 状态 0=下架 1=上架 */
    private Integer status;

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页数量 */
    private Integer pageSize = 10;
}
