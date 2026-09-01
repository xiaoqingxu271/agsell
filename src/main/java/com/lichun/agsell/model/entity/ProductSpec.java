package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品规格实体
 * 对应数据库表 product_spec
 */
@Data
@TableName("product_spec")
public class ProductSpec implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品ID */
    private Long productId;

    /** 规格名称（如500g装） */
    private String specName;

    /** 该规格价格 */
    private BigDecimal price;

    /** 该规格库存 */
    private Integer stock;

    /** 规格图片URL */
    private String image;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
