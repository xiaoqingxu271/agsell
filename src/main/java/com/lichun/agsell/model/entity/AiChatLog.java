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
 * AI 对话审计日志实体，对应数据库表 ai_chat_log
 * <p>
 * 每轮对话一条：Python 在回复下发后的收尾阶段旁路写入（失败只记日志不影响业务），
 * 管理端「对话审计」页只读，不做任何写接口——审计数据不允许被后台篡改。
 */
@Data
@TableName("ai_chat_log")
public class AiChatLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（游客/未登录为 NULL） */
    private Long userId;

    /** AI 会话号 */
    private String sessionId;

    /** 用户本轮提问原文 */
    private String question;

    /** AI 本轮回复 */
    private String reply;

    /** 意图标签（规则前置命中才非空） */
    private String intent;

    /** 本轮调用的工具，逗号分隔 */
    private String tools;

    /** 是否拒答轮 0/1 */
    private Integer refused;

    /** 知识库命中条数 */
    private Integer hits;

    /** 引用的 FAQ id，逗号分隔 */
    private String refs;

    /** 本轮总耗时毫秒 */
    private Integer durationMs;

    /**
     * 写入时间。表列名是 created_at（与 Python ORM 同列），不是项目惯例的 create_time——
     * 不映射的话 MyBatis-Plus 生成 SELECT ...,create_time 会直接 Unknown column，审计页整页 500
     */
    @TableField("created_at")
    private LocalDateTime createTime;
}
