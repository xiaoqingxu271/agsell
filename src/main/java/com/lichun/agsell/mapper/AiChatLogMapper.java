package com.lichun.agsell.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lichun.agsell.model.entity.AiChatLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话审计日志 Mapper（管理端只读）
 */
@Mapper
public interface AiChatLogMapper extends BaseMapper<AiChatLog> {
}
