package com.lichun.agsell.service;

import com.lichun.agsell.model.dto.SysLogQueryRequest;
import com.lichun.agsell.model.vo.SysLogPageVO;

/**
 * 操作日志服务
 */
public interface SysLogService {

    /** keyset 游标分页（create_time desc, id desc，避免深分页） */
    SysLogPageVO listLogs(SysLogQueryRequest request);
}
