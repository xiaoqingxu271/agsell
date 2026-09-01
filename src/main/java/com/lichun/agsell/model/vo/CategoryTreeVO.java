package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分类树节点 VO
 */
@Data
public class CategoryTreeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String icon;

    private Long parentId;

    private Integer sort;

    /** 子分类 */
    private java.util.List<CategoryTreeVO> children;

    private LocalDateTime createTime;
}
