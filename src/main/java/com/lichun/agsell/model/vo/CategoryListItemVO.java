package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分类列表 VO
 */
@Data
public class CategoryListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String icon;

    private Long parentId;

    private Integer sort;

    private Integer status;

    private LocalDateTime createTime;
}
