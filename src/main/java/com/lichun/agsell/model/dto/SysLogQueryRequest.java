package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 操作日志查询请求
 */
@Data
public class SysLogQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码 */
    private int pageNum = 1;

    /** 每页条数 */
    private int pageSize = 10;

    /** 模块筛选（可选） */
    private String module;

    /** 操作人姓名筛选（可选） */
    private String adminName;
}
