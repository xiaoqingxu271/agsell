package com.lichun.agsell.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lichun.agsell.model.entity.UserSearchLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户搜索词记录 Mapper
 */
@Mapper
public interface UserSearchLogMapper extends BaseMapper<UserSearchLog> {
}
