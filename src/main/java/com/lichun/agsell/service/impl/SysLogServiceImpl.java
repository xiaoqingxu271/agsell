package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.mapper.SysLogMapper;
import com.lichun.agsell.model.dto.SysLogQueryRequest;
import com.lichun.agsell.model.entity.SysLog;
import com.lichun.agsell.model.vo.SysLogVO;
import com.lichun.agsell.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志查询实现（只读，时间倒序）
 */
@Service
@RequiredArgsConstructor
public class SysLogServiceImpl implements SysLogService {

    private final SysLogMapper sysLogMapper;

    @Override
    public Page<SysLogVO> listLogs(SysLogQueryRequest request) {
        int pageNum = Math.max(request.getPageNum(), 1);
        int pageSize = Math.min(Math.max(request.getPageSize(), 1), 50);
        Page<SysLog> page = sysLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysLog>()
                        .eq(StringUtils.hasText(request.getModule()), SysLog::getModule, request.getModule())
                        .like(StringUtils.hasText(request.getAdminName()), SysLog::getAdminName, request.getAdminName())
                        .orderByDesc(SysLog::getCreateTime));
        Page<SysLogVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    private SysLogVO toVO(SysLog log) {
        SysLogVO vo = new SysLogVO();
        vo.setId(log.getId());
        vo.setAdminId(log.getAdminId());
        vo.setAdminName(log.getAdminName());
        vo.setModule(log.getModule());
        vo.setAction(log.getAction());
        vo.setContent(log.getContent());
        vo.setIp(log.getIp());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
