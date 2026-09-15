package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.mapper.SysLogMapper;
import com.lichun.agsell.model.dto.SysLogQueryRequest;
import com.lichun.agsell.model.entity.SysLog;
import com.lichun.agsell.model.vo.SysLogVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * 操作日志查询边界测试：分页钳制、筛选条件、时间倒序
 */
@ExtendWith(MockitoExtension.class)
class SysLogServiceImplTest {

    @Mock
    private SysLogMapper sysLogMapper;

    @InjectMocks
    private SysLogServiceImpl sysLogService;

    private SysLog sampleLog() {
        SysLog log = new SysLog();
        log.setId(1L);
        log.setAdminId(1L);
        log.setAdminName("系统管理员");
        log.setModule("认证");
        log.setAction("管理员登录");
        log.setContent("账号 admin 登录成功");
        log.setIp("127.0.0.1");
        log.setCreateTime(LocalDateTime.of(2026, 9, 15, 10, 0));
        return log;
    }

    @Test
    @DisplayName("分页：pageNum/pageSize 越界钳制（下限 1，上限 50）")
    void paginationClamped() {
        Page<SysLog> page = new Page<>(1, 10, 0);
        page.setRecords(List.of());
        when(sysLogMapper.selectPage(any(), any())).thenReturn(page);

        SysLogQueryRequest req = new SysLogQueryRequest();
        req.setPageNum(0);   // 越下界
        req.setPageSize(999); // 越上界
        sysLogService.listLogs(req);

        // 钳制必须体现在真正传给 DB 的分页参数上
        ArgumentCaptor<Page<SysLog>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(sysLogMapper).selectPage(pageCaptor.capture(), any());
        assertEquals(1, pageCaptor.getValue().getCurrent());
        assertEquals(50, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("筛选：module 精确 + adminName 模糊，VO 映射完整")
    void filterAndMapping() {
        SysLog log = sampleLog();
        Page<SysLog> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(log));
        when(sysLogMapper.selectPage(any(), any())).thenReturn(page);

        SysLogQueryRequest req = new SysLogQueryRequest();
        req.setModule("认证");
        req.setAdminName("系统");
        Page<SysLogVO> result = sysLogService.listLogs(req);

        assertEquals(1, result.getTotal());
        SysLogVO vo = result.getRecords().get(0);
        assertEquals("系统管理员", vo.getAdminName());
        assertEquals("认证", vo.getModule());
        assertEquals("管理员登录", vo.getAction());
        assertEquals("127.0.0.1", vo.getIp());
        assertEquals(log.getCreateTime(), vo.getCreateTime());
    }
}
