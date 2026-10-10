package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 评价回复草稿 VO（管理端「AI 生成回复」按钮）
 */
@Data
public class AiReviewReplyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评价 ID */
    private Long reviewId;

    /** AI 生成的回复草稿（管理员可编辑后再提交） */
    private String reply;

    /** llm = Agnes 生成；fallback = 规则模板兜底（Agnes 不可用时） */
    private String source;
}
