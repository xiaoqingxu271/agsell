package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价实体
 * 对应数据库表 review
 */
@Data
@TableName("review")
public class Review implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 订单明细ID（按明细评价，同一明细仅可评价一次） */
    private Long orderItemId;

    /** 商品ID */
    private Long productId;

    /** 用户ID */
    private Long userId;

    /** 评分 1-5 */
    private Integer rating;

    /** 情感标签 1=好评 0=中评 -1=差评（NULL=未分析，由 AI 服务批量分析回写） */
    private Integer sentimentLabel;

    /** 情感得分 0~1，越大越正面 */
    private BigDecimal sentimentScore;

    /** 情感关键词（逗号分隔，最多5个） */
    private String sentimentKeywords;

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
