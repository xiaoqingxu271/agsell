package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端溯源信息详情 VO（含生产记录）
 */
@Data
public class TraceabilityDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long productId;

    /** 商品名称（联查） */
    private String productName;

    /** 商品主图（联查） */
    private String productImage;

    /** 批次号 */
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

    /** 认证类型 */
    private String certificationType;

    /** 认证类型文本 */
    private String certificationTypeText;

    /** 认证证书图片 */
    private List<String> certificationUrls;

    /** 溯源二维码URL */
    private String qrCodeUrl;

    /** 生产记录（按记录日期升序） */
    private List<ProductionRecordVO> productionRecords;

    private LocalDateTime createTime;
}
