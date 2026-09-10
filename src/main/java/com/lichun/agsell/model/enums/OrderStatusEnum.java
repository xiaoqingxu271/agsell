package com.lichun.agsell.model.enums;

import lombok.Getter;

/**
 * 订单状态枚举（order.status）
 * 全局唯一的状态定义：状态码 + 状态文本 + 查询工具，替换散落在各 Service 中的魔法数字与重复的状态文本映射。
 */
@Getter
public enum OrderStatusEnum {

    /** 待付款 */
    PENDING_PAYMENT(0, "待付款"),
    /** 待发货（已支付） */
    PENDING_SHIPMENT(1, "待发货"),
    /** 待收货 */
    PENDING_RECEIPT(2, "待收货"),
    /** 已完成 */
    COMPLETED(3, "已完成"),
    /** 已取消 */
    CANCELLED(4, "已取消"),
    /** 售后处理中 */
    AFTER_SALES(5, "售后处理中"),
    /** 已退款 */
    REFUNDED(6, "已退款");

    private final int code;
    private final String text;

    OrderStatusEnum(int code, String text) {
        this.code = code;
        this.text = text;
    }

    /**
     * 根据状态码获取状态文本；未知状态返回"未知"（替代各处 getOrDefault(status, "未知")）
     */
    public static String textOf(Integer code) {
        if (code == null) {
            return "未知";
        }
        for (OrderStatusEnum e : values()) {
            if (e.code == code) {
                return e.text;
            }
        }
        return "未知";
    }
}
