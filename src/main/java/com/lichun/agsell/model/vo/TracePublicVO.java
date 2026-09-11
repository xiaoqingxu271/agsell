package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 用户端溯源档案 VO（扫码/详情入口展示）
 */
@Data
public class TracePublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 商品主图 */
    private String productImage;

    /** 批次号 */
    private String batchNo;

    /** 种植户姓名 */
    private String farmerName;

    /** 种植户电话 */
    private String farmerPhone;

    /** 产地（省市区拼接） */
    private String origin;

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

    /** 新鲜度（harvest_date 为空时为 null） */
    private FreshnessVO freshness;

    /** 生产记录（按记录日期升序） */
    private List<ProductionRecordVO> productionRecords;

    @Data
    public static class FreshnessVO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 等级 FRESH / NORMAL / FAIR / STALE */
        private String level;

        /** 等级文本 */
        private String levelText;

        /** 距采摘天数 */
        private long daysSinceHarvest;
    }
}
