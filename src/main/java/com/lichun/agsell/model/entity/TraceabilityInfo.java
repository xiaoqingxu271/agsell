package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 溯源信息实体
 * 对应数据库表 traceability_info
 */
@Data
@TableName("traceability_info")
public class TraceabilityInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品ID */
    private Long productId;

    /** 批次号（唯一，生成后不可修改） */
    private String batchNo;

    /** 种植户姓名 */
    private String farmerName;

    /** 种植户电话 */
    private String farmerPhone;

    /** 原产省份 */
    private String originProvince;

    /** 原产城市 */
    private String originCity;

    /** 原产区县 */
    private String originDistrict;

    /** 种植日期 */
    private LocalDate plantingDate;

    /** 采摘日期 */
    private LocalDate harvestDate;

    /** 质检结果 */
    private String qualityCheckResult;

    /** 农药残留检测结果 */
    private String pesticideTest;

    /** 认证类型 ORGANIC=有机 GREEN=绿色 GEOGRAPHICAL=地理标志 NONE=无 */
    private String certificationType;

    /** 认证证书图片(JSON数组) */
    private String certificationUrls;

    /** 溯源二维码URL */
    private String qrCodeUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
