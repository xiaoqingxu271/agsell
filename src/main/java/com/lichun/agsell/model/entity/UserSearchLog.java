package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户搜索词记录实体
 * 对应数据库表 user_search_log
 * <p>
 * 登录用户的搜索词留存，供 AI 侧偏好聚合（关键词 → 品类映射）。
 * 游客搜索不落库；与全站热词表（search_hot_word）职责互补，互不替代。
 */
@Data
@TableName("user_search_log")
public class UserSearchLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 搜索词 */
    private String keyword;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
