package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 * 对应数据库表 order
 */
@Data
@TableName("`order`")
public class Order implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 商品总金额 */
    private BigDecimal totalAmount;

    /** 运费 */
    private BigDecimal freight;

    /** 优惠金额 */
    private BigDecimal discount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 状态 0=待付款 1=待发货 2=待收货 3=已完成 4=已取消 5=售后处理中 6=已退款 */
    private Integer status;

    /** 收货地址ID */
    private Long addressId;

    /** 收件人（快照） */
    private String receiver;

    /** 收件电话（快照） */
    private String phone;

    /** 详细地址（快照） */
    private String address;

    /** 支付方式 1=模拟支付 */
    private Integer payType;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime deliveryTime;

    /** 确认收货时间 */
    private LocalDateTime receiveTime;

    /** 物流公司 */
    private String logType;

    /** 物流单号 */
    private String logNo;

    /** 买家备注 */
    private String remark;

    /** 取消原因 */
    private String cancelReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
