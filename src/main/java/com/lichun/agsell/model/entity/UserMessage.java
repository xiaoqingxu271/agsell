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
 * 用户站内消息实体
 * 对应数据库表 user_message
 * <p>
 * 由管理员触发的节点写入（发货、售后处理结果），小程序「消息中心」页展示。
 */
@Data
@TableName("user_message")
public class UserMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收用户ID */
    private Long userId;

    /** 消息类型：order=订单/物流 after_sales=售后结果 */
    private String type;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 关联单号（订单号/售后单号，用于跳转） */
    private String relatedNo;

    /** 0=未读 1=已读 */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
