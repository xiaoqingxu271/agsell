package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商品实体
 * 对应数据库表 product
 */
@Data
@TableName("product")
public class Product implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品名称 */
    private String name;

    /** 副标题 */
    private String subtitle;

    /** 分类ID */
    private Long categoryId;

    /** 售价 */
    private BigDecimal price;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 库存 */
    private Integer stock;

    /** 销量 */
    private Integer sales;

    /** 主图URL */
    private String mainImage;

    /** 图片数组JSON */
    private String images;

    /** 商品详情HTML */
    private String description;

    /** 产地 */
    private String origin;

    /** 采摘/上市日期 */
    private LocalDate harvestDate;

    /** 保质期 */
    private String shelfLife;

    /** 储存方式 */
    private String storage;

    /** 状态 0=下架 1=上架 */
    private Integer status;

    /** 排序值 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
