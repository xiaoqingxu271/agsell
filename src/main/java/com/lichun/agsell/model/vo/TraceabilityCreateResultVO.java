package com.lichun.agsell.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增溯源信息结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraceabilityCreateResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 溯源信息ID */
    private Long id;

    /** 批次号 */
    private String batchNo;
}
