package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 生产记录请求（新增/编辑共用）
 */
@Data
public class ProductionRecordRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 溯源信息ID（新增时指定，编辑时无需） */
    private Long traceabilityId;

    /** 记录类型 SEEDING=播种 FERTILIZING=施肥 WATERING=浇水 PEST_CONTROL=病虫害防治 HARVEST=采收 OTHER=其他 */
    private String recordType;

    /** 记录日期 */
    private LocalDate recordDate;

    /** 记录内容 */
    private String content;

    /** 记录图片 */
    private List<String> images;

    /** 操作人 */
    private String operator;
}
