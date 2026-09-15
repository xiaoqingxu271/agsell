package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.SysLogQueryRequest;
import com.lichun.agsell.model.vo.SysLogVO;

/**
 * 操作日志服务
 */
public interface SysLogService {

    /** 分页查询（时间倒序，支持模块/操作人筛选） */
    Page<SysLogVO> listLogs(SysLogQueryRequest request);
}
