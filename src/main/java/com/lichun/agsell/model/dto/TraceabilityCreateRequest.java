package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增溯源信息请求
 */
@Data
public class TraceabilityCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID */
    private Long productId;

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

    /** 认证证书图片 */
    private List<String> certificationUrls;

    /** 生产记录 */
    private List<ProductionRecordRequest> productionRecords;
}
