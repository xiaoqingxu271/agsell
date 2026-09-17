package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lichun.agsell.mapper.SysLogMapper;
import com.lichun.agsell.model.dto.SysLogQueryRequest;
import com.lichun.agsell.model.entity.SysLog;
import com.lichun.agsell.model.vo.SysLogPageVO;
import com.lichun.agsell.model.vo.SysLogVO;
import org.junit.jupiter.api.BeforeEach;
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
 * 操作日志双模分页边界测试：OFFSET 跳页 / keyset 游标 / size 钳制 / 筛选映射 / hasMore
 */
@ExtendWith(MockitoExtension.class)
class SysLogServiceImplTest {

    @Mock
    private SysLogMapper sysLogMapper;

    @InjectMocks
    private SysLogServiceImpl sysLogService;

    @BeforeEach
    void initTableInfo() {
        // 纯 Mockito 环境需要手动初始化 MP 的 lambda 元数据缓存
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), SysLog.class);
    }

    private SysLog sampleLog(long id, LocalDateTime time) {
        SysLog log = new SysLog();
        log.setId(id);
        log.setAdminId(1L);
        log.setAdminName("系统管理员");
        log.setModule("认证");
        log.setAction("管理员登录");
        log.setContent("账号 admin 登录成功");
        log.setIp("127.0.0.1");
        log.setCreateTime(time);
        return log;
    }

    @Test
    @DisplayName("pageSize 越界钳制（下限 1，上限 50）— OFFSET 分支")
    void sizeClamped() {
        when(sysLogMapper.selectList(any())).thenReturn(List.of());
        when(sysLogMapper.selectCount(any())).thenReturn(0L);

        SysLogQueryRequest req = new SysLogQueryRequest();
        req.setPageSize(999); // 越上界
        sysLogService.listLogs(req);

        // 钳制必须体现在真正传给 DB 的 LIMIT 上（OFFSET 分支：LIMIT offset, size）
        ArgumentCaptor<LambdaQueryWrapper<SysLog>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sysLogMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getCustomSqlSegment().contains("LIMIT 0, 50"));
    }

    @Test
    @DisplayName("筛选：module 精确 + adminName 模糊，VO 映射完整，OFFSET 分支返回游标")
    void filterAndMapping() {
        SysLog log = sampleLog(1L, LocalDateTime.of(2026, 9, 15, 10, 0));
        when(sysLogMapper.selectList(any())).thenReturn(List.of(log));
        when(sysLogMapper.selectCount(any())).thenReturn(1L);

        SysLogQueryRequest req = new SysLogQueryRequest();
        req.setModule("认证");
        req.setAdminName("系统");
        SysLogPageVO result = sysLogService.listLogs(req);

        assertEquals(1, result.getTotal());
        assertFalse(result.getHasMore());
        assertEquals(1L, result.getCursorId());
        assertEquals(log.getCreateTime(), result.getCursorTime());
        SysLogVO vo = result.getRecords().get(0);
        assertEquals("系统管理员", vo.getAdminName());
        assertEquals("认证", vo.getModule());
        assertEquals("管理员登录", vo.getAction());
        assertEquals("127.0.0.1", vo.getIp());
        assertEquals(log.getCreateTime(), vo.getCreateTime());
    }

    @Test
    @DisplayName("keyset 分支：携带游标时返回 size+1 则截断为 size 并标记还有下一页")
    void keysetBranch_hasMoreDetection() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 16, 10, 0);
        List<SysLog> eleven = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> sampleLog(100L + i, t1.minusSeconds(i))).toList();
        when(sysLogMapper.selectList(any())).thenReturn(eleven);
        when(sysLogMapper.selectCount(any())).thenReturn(72L);

        SysLogQueryRequest req = new SysLogQueryRequest();
        req.setPageSize(10);
        req.setCursorTime(t1);
        req.setCursorId(999L);
        SysLogPageVO result = sysLogService.listLogs(req);

        assertTrue(result.getHasMore());
        assertEquals(10, result.getRecords().size());
        // 下一页游标 = 截断后最后一条（第 10 条）
        assertEquals(100L + 10, result.getCursorId());
        assertEquals(t1.minusSeconds(10), result.getCursorTime());
        // keyset 分支必须多取 1 条（LIMIT size+1）
        ArgumentCaptor<LambdaQueryWrapper<SysLog>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sysLogMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getCustomSqlSegment().contains("LIMIT 11"));
    }

    @Test
    @DisplayName("OFFSET 分支：hasMore 由偏移与总数推算，不截断数据")
    void offsetBranch_hasMoreByTotal() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 16, 10, 0);
        List<SysLog> ten = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> sampleLog(100L + i, t1.minusSeconds(i))).toList();
        when(sysLogMapper.selectList(any())).thenReturn(ten);
        when(sysLogMapper.selectCount(any())).thenReturn(35L); // 第 3 页（offset=20）→ 30 < 35 还有下一页

        SysLogQueryRequest req = new SysLogQueryRequest();
        req.setPageNum(3);
        req.setPageSize(10);
        SysLogPageVO result = sysLogService.listLogs(req);

        assertEquals(10, result.getRecords().size());
        assertTrue(result.getHasMore());
        assertEquals(100L + 10, result.getCursorId());
        ArgumentCaptor<LambdaQueryWrapper<SysLog>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sysLogMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getCustomSqlSegment().contains("LIMIT 20, 10"));
    }
}
