package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 秒杀下单请求（用户端）
 */
@Data
public class SeckillOrderRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 秒杀活动编号（对外暴露的随机标识，替代自增ID防爬取枚举） */
    private String activityCode;

    /** 收货地址ID */
    private Long addressId;

    /** 买家备注 */
    private String remark;
}
