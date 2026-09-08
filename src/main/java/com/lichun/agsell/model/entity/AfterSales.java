package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单实体
 * 对应数据库表 after_sales
 */
@Data
@TableName("after_sales")
public class AfterSales implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 售后单号 */
    private String afterSalesNo;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 申请时订单状态 2=待收货 3=已完成 */
    private Integer originalStatus;

    /** 售后类型 1=仅退款 2=退货退款 */
    private Integer type;

    /** 原因类型 QUALITY=品质问题(坏果包赔) WRONG_ITEM=发错货 MISSING_ITEM=少件漏发 OTHER=其他 */
    private String reasonType;

    /** 问题描述 */
    private String reason;

    /** 凭证图片(JSON数组) */
    private String images;

    /** 申请退款金额 */
    private BigDecimal refundAmount;

    /** 状态 0=待处理 1=已同意(退款完成) 2=已拒绝 3=已撤销 */
    private Integer status;

    /** 处理意见 */
    private String handleRemark;

    /** 处理人(管理员ID) */
    private Long handleBy;

    /** 处理时间 */
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
