package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.AiTicketMapper;
import com.lichun.agsell.model.entity.AiTicket;
import com.lichun.agsell.model.vo.AiTicketVO;
import com.lichun.agsell.service.AiTicketService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI 转人工工单服务实现
 */
@Service
@RequiredArgsConstructor
public class AiTicketServiceImpl implements AiTicketService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_HANDLED = 1;

    private static final String STATUS_TEXT_PENDING = "待处理";
    private static final String STATUS_TEXT_HANDLED = "已处理";

    private final AiTicketMapper aiTicketMapper;

    @Override
    public Page<AiTicketVO> page(int pageNum, int pageSize, Integer status) {
        Page<AiTicket> page = aiTicketMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AiTicket>()
                        .eq(status != null, AiTicket::getStatus, status)
                        .orderByAsc(AiTicket::getStatus)
                        .orderByDesc(AiTicket::getCreateTime));

        Page<AiTicketVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Override
    public void handle(Long id, String remark, Long adminId) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "工单ID不能为空");
        // 原子抢占处理权：仅「待处理 → 已处理」生效，双管理员并发处理只成功一次
        int claimed = aiTicketMapper.update(null, new LambdaUpdateWrapper<AiTicket>()
                .eq(AiTicket::getId, id)
                .eq(AiTicket::getStatus, STATUS_PENDING)
                .set(AiTicket::getStatus, STATUS_HANDLED)
                .set(AiTicket::getHandleRemark, remark)
                .set(AiTicket::getHandleBy, adminId)
                .set(AiTicket::getHandleTime, LocalDateTime.now()));
        ThrowUtils.throwIf(claimed == 0, ErrorCode.OPERATION_ERROR, "该工单已处理，请勿重复操作");
    }

    private AiTicketVO toVO(AiTicket ticket) {
        AiTicketVO vo = new AiTicketVO();
        vo.setId(ticket.getId());
        vo.setUserId(ticket.getUserId());
        vo.setSessionId(ticket.getSessionId());
        vo.setQuestion(ticket.getQuestion());
        vo.setReason(ticket.getReason());
        vo.setStatus(ticket.getStatus());
        vo.setStatusText(ticket.getStatus() != null && ticket.getStatus() == STATUS_HANDLED
                ? STATUS_TEXT_HANDLED : STATUS_TEXT_PENDING);
        vo.setHandleRemark(ticket.getHandleRemark());
        vo.setHandleBy(ticket.getHandleBy());
        vo.setHandleTime(ticket.getHandleTime());
        vo.setCreateTime(ticket.getCreateTime());
        return vo;
    }
}
