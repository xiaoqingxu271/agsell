package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 我的评价 VO
 */
@Data
public class ReviewMyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 商品图片 */
    private String productImage;

    /** 规格名称 */
    private String specName;

    /** 评分 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 评价图片 */
    private List<String> images;

    /** 卖家回复 */
    private String replyContent;

    /** 回复时间 */
    private LocalDateTime replyTime;

    private LocalDateTime createTime;
}
