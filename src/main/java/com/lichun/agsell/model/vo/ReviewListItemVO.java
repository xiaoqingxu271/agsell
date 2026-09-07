package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端评价列表项 VO
 */
@Data
public class ReviewListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private Long productId;

    /** 商品名称 */
    private String productName;

    private Long userId;

    /** 用户名 */
    private String userName;

    /** 评分 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 是否已回复 */
    private Boolean replied;

    /** 卖家回复内容 */
    private String replyContent;

    private Integer isAnonymous;

    private LocalDateTime createTime;
}
