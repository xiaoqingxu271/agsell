package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 内部接口查询请求（Python 客服服务回调）
 */
@Data
public class AiInternalQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID（必填，Python 侧从会话上下文传入） */
    private Long userId;

    /** 订单号（查详情/物流时必填） */
    private String orderNo;

    /** 状态筛选（可选） */
    private Integer status;
}
