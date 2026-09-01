package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价 VO（用户端/管理端共用）
 */
@Data
public class ReviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private Long productId;

    private Long userId;

    /** 用户名（匿名时隐藏） */
    private String userName;

    /** 用户头像 */
    private String userAvatar;

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

    /** 是否匿名 */
    private Integer isAnonymous;

    private LocalDateTime createTime;
}
