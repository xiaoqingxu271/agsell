package com.lichun.agsell.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lichun.agsell.model.entity.AiTicket;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 转人工工单 Mapper
 */
@Mapper
public interface AiTicketMapper extends BaseMapper<AiTicket> {
}
