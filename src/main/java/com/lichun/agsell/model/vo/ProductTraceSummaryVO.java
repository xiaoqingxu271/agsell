package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品溯源摘要 VO（商品详情页溯源入口）
 */
@Data
public class ProductTraceSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否有溯源信息 */
    private boolean hasTrace;

    /** 最新批次号 */
    private String traceBatchNo;

    /** 溯源二维码URL */
    private String qrCodeUrl;
}
