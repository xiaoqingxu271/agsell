package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户商品浏览足迹实体
 * 对应数据库表 user_product_view，粒度为「用户 × 商品 × 自然日」
 * <p>
 * 作为 AI 个性化推荐的实时行为信号（ai-service 只读本表算类目偏好），
 * 同日重复浏览只累加 view_count，偏好聚合按日去重不叠加权重。
 */
@Data
@TableName("user_product_view")
public class UserProductView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键：雪花ID（应用层生成，避免暴露自增序号） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 商品ID */
    private Long productId;

    /** 类目ID（冗余存储，偏好聚合免 join product） */
    private Long categoryId;

    /** 浏览自然日 */
    private LocalDate viewDate;

    /** 当日浏览次数（仅统计，不参与偏好权重） */
    private Integer viewCount;

    /** 当日最后一次浏览时间 */
    private LocalDateTime lastViewTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
