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
 * AI 转人工工单实体
 * 对应数据库表 ai_ticket
 * <p>
 * Python 侧触发 escalate_human 后回调 POST /api/ai/internal/ticket 落库，
 * 管理后台「AI 工单」页处理。游客转人工同样留痕，userId 可为空。
 */
@Data
@TableName("ai_ticket")
public class AiTicket implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（游客转人工为 NULL） */
    private Long userId;

    /** AI 会话号 */
    private String sessionId;

    /** 触发转人工的用户原话 */
    private String question;

    /** 转人工原因（工具入参/规则判定） */
    private String reason;

    /** 状态 0=待处理 1=已处理 */
    private Integer status;

    /** 处理备注 */
    private String handleRemark;

    /** 处理管理员ID */
    private Long handleBy;

    /** 处理时间 */
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
