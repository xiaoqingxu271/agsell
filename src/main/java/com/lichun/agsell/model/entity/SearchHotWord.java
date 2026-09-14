package com.lichun.agsell.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 搜索热词实体
 * 对应数据库表 search_hot_word
 */
@Data
@TableName("search_hot_word")
public class SearchHotWord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键：雪花ID（应用层生成，避免暴露自增序号） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 搜索词 */
    private String word;

    /** 累计搜索次数 */
    private Integer searchCount;

    /** 排序值，越大越前 */
    private Integer sort;

    /** 0=隐藏 1=展示 */
    private Integer status;

    /** 0=自动统计 1=管理员手工配置 */
    private Integer isManual;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
