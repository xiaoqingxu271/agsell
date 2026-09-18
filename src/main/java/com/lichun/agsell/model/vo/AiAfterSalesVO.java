package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 售后单 VO（供客服回答"我的退款/售后进度"）
 */
@Data
public class AiAfterSalesVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String afterSalesNo;

    private String orderNo;

    /** 售后类型 1=仅退款 2=退货退款 */
    private Integer type;

    /** 类型文本 */
    private String typeText;

    /** 原因类型 QUALITY/WRONG_ITEM/MISSING_ITEM/OTHER */
    private String reasonType;

    /** 原因类型文本 */
    private String reasonTypeText;

    private String reason;

    private BigDecimal refundAmount;

    /** 状态 0=待处理 1=已同意(退款完成) 2=已拒绝 3=已撤销 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

    private String handleRemark;

    private LocalDateTime createTime;

    private LocalDateTime handleTime;
}
