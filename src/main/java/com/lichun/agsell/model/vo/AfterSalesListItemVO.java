package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户端售后列表项 VO
 */
@Data
public class AfterSalesListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 售后单号 */
    private String afterSalesNo;

    /** 订单号 */
    private String orderNo;

    /** 售后类型 1=仅退款 2=退货退款 */
    private Integer type;

    /** 售后类型文本 */
    private String typeText;

    /** 原因类型 */
    private String reasonType;

    /** 原因类型文本 */
    private String reasonTypeText;

    /** 申请退款金额 */
    private BigDecimal refundAmount;

    /** 状态 0=待处理 1=已同意 2=已拒绝 3=已撤销 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

    /** 问题描述（列表截断展示） */
    private String reason;

    /** 处理意见 */
    private String handleRemark;

    private LocalDateTime createTime;

    private LocalDateTime handleTime;
}
