package com.lichun.agsell.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志查询请求（keyset 游标分页：create_time desc, id desc）
 */
@Data
public class SysLogQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码（兼容旧调用，键集分页下仅供 total 展示，不参与偏移） */
    private int pageNum = 1;

    /** 每页条数（上限 50） */
    private int pageSize = 10;

    /** 模块筛选（可选） */
    private String module;

    /** 操作人姓名筛选（可选） */
    private String adminName;

    /** 游标：上一页最后一条 createTime（yyyy-MM-dd HH:mm:ss） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cursorTime;

    /** 游标：上一页最后一条 id */
    private Long cursorId;
}
