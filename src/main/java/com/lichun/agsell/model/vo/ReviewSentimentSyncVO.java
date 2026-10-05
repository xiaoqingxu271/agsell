package com.lichun.agsell.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评价情感分析同步结果 VO（管理端触发）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSentimentSyncVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本次分析回写的评价数 */
    private Integer analyzedCount;

    /** 剩余未分析的评价数（0 表示已全部覆盖） */
    private Integer remainingCount;
}
