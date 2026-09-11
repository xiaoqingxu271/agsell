package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产记录 VO
 */
@Data
public class ProductionRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 记录类型 */
    private String recordType;

    /** 记录类型文本 */
    private String recordTypeText;

    /** 记录日期 */
    private LocalDate recordDate;

    /** 记录内容 */
    private String content;

    /** 记录图片 */
    private List<String> images;

    /** 操作人 */
    private String operator;

    private LocalDateTime createTime;
}
