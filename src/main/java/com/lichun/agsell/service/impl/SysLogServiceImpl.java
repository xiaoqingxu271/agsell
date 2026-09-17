package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.mapper.SysLogMapper;
import com.lichun.agsell.model.dto.SysLogQueryRequest;
import com.lichun.agsell.model.entity.SysLog;
import com.lichun.agsell.model.vo.SysLogPageVO;
import com.lichun.agsell.model.vo.SysLogVO;
import com.lichun.agsell.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 操作日志查询实现（只读，游标 + 页码双模分页）
 *
 * <p>深分页优化：顺序翻页（上一页/下一页）携带上一页最后一条 (create_time, id) 走 keyset 游标，
 * 经 (create_time, id) 复合索引直接定位，每页恒定只扫 pageSize+1 行，与页数无关；
 * 任意跳页（点击页码）走 OFFSET 兜底，复合索引反向扫描保证排序稳定且可接受。
 * 排序键 create_time desc, id desc 保证同秒数据次序稳定，翻页不重不漏。
 */
@Service
@RequiredArgsConstructor
public class SysLogServiceImpl implements SysLogService {

    private final SysLogMapper sysLogMapper;

    @Override
    public SysLogPageVO listLogs(SysLogQueryRequest request) {
        int pageNum = Math.max(request.getPageNum(), 1);
        int pageSize = Math.min(Math.max(request.getPageSize(), 1), 50);

        // 基础筛选（module 精确 + adminName 模糊），count 与查询共用
        LambdaQueryWrapper<SysLog> qw = new LambdaQueryWrapper<SysLog>()
                .eq(StringUtils.hasText(request.getModule()), SysLog::getModule, request.getModule())
                .like(StringUtils.hasText(request.getAdminName()), SysLog::getAdminName, request.getAdminName())
                .orderByDesc(SysLog::getCreateTime).orderByDesc(SysLog::getId);

        long total = sysLogMapper.selectCount(qw);
        boolean hasMore;
        List<SysLog> logs;

        if (request.getCursorTime() != null) {
            // ── keyset 游标分支：上一页最后一条 (create_time, id) 之后的数据 ──
            qw.and(w -> w
                    .lt(SysLog::getCreateTime, request.getCursorTime())
                    .or(o -> o.eq(SysLog::getCreateTime, request.getCursorTime())
                            .lt(SysLog::getId, request.getCursorId())));
            // 多取 1 条判断是否还有下一页
            logs = sysLogMapper.selectList(qw.last("LIMIT " + (pageSize + 1)));
            hasMore = logs.size() > pageSize;
            if (hasMore) {
                logs = logs.subList(0, pageSize);
            }
        } else {
            // ── 页码 OFFSET 分支：跳页兜底（LIMIT offset, size）──
            long offset = (long) (pageNum - 1) * pageSize;
            logs = sysLogMapper.selectList(qw.last("LIMIT " + offset + ", " + pageSize));
            hasMore = offset + pageSize < total;
        }

        SysLogPageVO vo = new SysLogPageVO();
        vo.setRecords(logs.stream().map(this::toVO).toList());
        vo.setTotal(total);
        vo.setHasMore(hasMore);
        if (!logs.isEmpty()) {
            // 下一页游标 = 本页最后一条
            SysLog last = logs.get(logs.size() - 1);
            vo.setCursorTime(last.getCreateTime());
            vo.setCursorId(last.getId());
        }
        return vo;
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
