package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评价实体
 * 对应数据库表 review
 */
@Data
@TableName("review")
public class Review implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 商品ID */
    private Long productId;

    /** 用户ID */
    private Long userId;

    /** 评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 评价图片JSON数组 */
    private String images;

    /** 卖家回复 */
    private String replyContent;

    /** 回复时间 */
    private LocalDateTime replyTime;

    /** 是否匿名 0=否 1=是 */
    private Integer isAnonymous;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
