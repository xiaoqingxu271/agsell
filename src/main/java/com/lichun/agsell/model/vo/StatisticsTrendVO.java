package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端数据趋势统计（近 N 天）
 */
@Data
public class StatisticsTrendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日期列表（yyyy-MM-dd，升序） */
    private List<String> dates;

    /** 每日新增用户数（按创建时间） */
    private List<Long> newUsers;

    /** 每日订单数（按创建时间） */
    private List<Long> orderCounts;

    /** 每日销售额（已支付订单，按支付时间） */
    private List<BigDecimal> sales;
}
