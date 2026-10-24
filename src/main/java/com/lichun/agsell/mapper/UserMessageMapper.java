package com.lichun.agsell.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lichun.agsell.model.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户站内消息 Mapper
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}
