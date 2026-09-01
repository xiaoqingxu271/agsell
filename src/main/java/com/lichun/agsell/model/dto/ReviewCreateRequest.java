package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 提交评价请求
 */
@Data
public class ReviewCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单ID */
    private Long orderId;

    /** 评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 评价图片URL数组 */
    private List<String> images;

    /** 是否匿名 0=否 1=是 */
    private Integer isAnonymous;
}
