package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端订单详情 VO
 */
@Data
public class AdminOrderDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;

    private Long userId;

    private String username;

    private String nickname;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    private BigDecimal freight;

    private BigDecimal discount;

    /** 订单状态 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

    private String receiver;

    private String phone;

    private String address;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime deliveryTime;

    private LocalDateTime receiveTime;

    private String logType;

    private String logNo;

    private String cancelReason;

    /** 订单明细 */
    private List<OrderDetailVO.OrderItemVO> items;
}
