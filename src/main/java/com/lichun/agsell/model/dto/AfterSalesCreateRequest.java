package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 申请售后请求
 */
@Data
public class AfterSalesCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 售后类型 1=仅退款 2=退货退款 */
    private Integer type;

    /** 原因类型 QUALITY/WRONG_ITEM/MISSING_ITEM/OTHER */
    private String reasonType;

    /** 问题描述 */
    private String reason;

    /** 凭证图片URL数组 */
    private List<String> images;

    /** 申请退款金额（默认订单实付金额） */
    private BigDecimal refundAmount;
}
