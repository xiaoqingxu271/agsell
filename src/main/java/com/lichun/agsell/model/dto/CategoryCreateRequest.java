package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品分类新增/编辑请求
 */
@Data
public class CategoryCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类ID（编辑时必填） */
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类图标URL */
    private String icon;

    /** 父分类ID，0 表示一级分类 */
    private Long parentId;

    /** 排序值 */
    private Integer sort;
}
