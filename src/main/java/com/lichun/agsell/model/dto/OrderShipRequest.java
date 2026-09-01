package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发货请求（管理端）
 */
@Data
public class OrderShipRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 物流公司 */
    private String logType;

    /** 物流单号 */
    private String logNo;
}
