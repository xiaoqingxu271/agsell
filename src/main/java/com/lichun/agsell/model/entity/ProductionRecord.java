package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产记录实体
 * 对应数据库表 production_record
 */
@Data
@TableName("production_record")
public class ProductionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品ID（冗余，便于按商品查询） */
    private Long productId;

    /** 批次号 */
    private String batchNo;

    /** 记录类型 SEEDING=播种 FERTILIZING=施肥 WATERING=浇水 PEST_CONTROL=病虫害防治 HARVEST=采收 OTHER=其他 */
    private String recordType;

    /** 记录日期 */
    private LocalDate recordDate;

    /** 记录内容 */
    private String content;

    /** 记录图片(JSON数组) */
    private String images;

    /** 操作人 */
    private String operator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
