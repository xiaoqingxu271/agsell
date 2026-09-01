package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品分类实体
 * 对应数据库表 product_category
 */
@Data
@TableName("product_category")
public class ProductCategory implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类图标URL */
    private String icon;

    /** 父分类ID，0 表示一级分类 */
    private Long parentId;

    /** 排序值，越大越靠前 */
    private Integer sort;

    /** 状态 0=禁用 1=启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
