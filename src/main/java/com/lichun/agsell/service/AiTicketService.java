package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.vo.AiTicketVO;

/**
 * AI 转人工工单服务（管理端）
 */
public interface AiTicketService {

    /** 分页查询工单（status 为空查全部） */
    Page<AiTicketVO> page(int pageNum, int pageSize, Integer status);

    /**
     * 处理工单：待处理 → 已处理（原子抢占，防双管理员并发重复处理）
     *
     * @param id      工单ID
     * @param remark  处理备注
     * @param adminId 处理管理员ID
     */
    void handle(Long id, String remark, Long adminId);
}
