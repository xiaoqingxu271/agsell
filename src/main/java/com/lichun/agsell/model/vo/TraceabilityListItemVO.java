package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端溯源信息列表项 VO
 */
@Data
public class TraceabilityListItemVO implements Serializable {

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

    /** 产地（省市区拼接） */
    private String origin;

    /** 采摘日期 */
    private LocalDate harvestDate;

    /** 认证类型 */
    private String certificationType;

    /** 认证类型文本 */
    private String certificationTypeText;

    /** 溯源二维码URL */
    private String qrCodeUrl;

    private LocalDateTime createTime;
}
