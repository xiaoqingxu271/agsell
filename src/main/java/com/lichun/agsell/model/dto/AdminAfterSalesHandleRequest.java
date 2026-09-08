package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理端处理售后请求
 */
@Data
public class AdminAfterSalesHandleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否同意退款（true=同意，false=拒绝） */
    private Boolean agree;

    /** 处理意见 */
    private String remark;
}
