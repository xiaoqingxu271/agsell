package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端售后详情 VO
 */
@Data
public class AdminAfterSalesDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 售后单号 */
    private String afterSalesNo;

    /** 订单号 */
    private String orderNo;

    private Long userId;

    /** 用户名 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 订单实付金额 */
    private BigDecimal orderPayAmount;

    /** 收件人 */
    private String receiver;

    /** 收件电话 */
    private String phone;

    /** 收货地址 */
    private String address;

    /** 申请时订单状态 */
    private Integer originalStatus;

    /** 订单原状态文本 */
    private String originalStatusText;

    /** 售后类型 1=仅退款 2=退货退款 */
    private Integer type;

    /** 售后类型文本 */
    private String typeText;

    /** 原因类型 */
    private String reasonType;

    /** 原因类型文本 */
    private String reasonTypeText;

    /** 问题描述 */
    private String reason;

    /** 凭证图片 */
    private List<String> images;

    /** 申请退款金额 */
    private BigDecimal refundAmount;

    /** 状态 0=待处理 1=已同意 2=已拒绝 3=已撤销 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

    /** 处理意见 */
    private String handleRemark;

    /** 处理人ID */
    private Long handleBy;

    /** 处理时间 */
    private LocalDateTime handleTime;

    private LocalDateTime createTime;

    /** 订单商品明细 */
    private List<OrderDetailVO.OrderItemVO> items;
}
