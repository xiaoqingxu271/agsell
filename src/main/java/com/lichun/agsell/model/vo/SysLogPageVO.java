package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志分页响应（keyset 游标分页）
 */
@Data
public class SysLogPageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<SysLogVO> records;

    /** 总条数（count 查询，用于前端展示） */
    private Long total;

    /** 是否还有下一页 */
    private Boolean hasMore;

    /** 下一页游标：本页最后一条 createTime */
    private LocalDateTime cursorTime;

    /** 下一页游标：本页最后一条 id */
    private Long cursorId;
}
