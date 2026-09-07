package com.lichun.agsell.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 分页查询用户列表（关联默认收货地址获取手机号）
     */
    @Select("<script>" +
            "SELECT u.id, u.username, u.nickname, u.avatar, " +
            "(SELECT a.phone FROM user_address a WHERE a.user_id = u.id AND a.deleted = 0 AND a.is_default = 1 LIMIT 1) AS phone, " +
            "u.status, u.login_time, u.create_time " +
            "FROM sys_user u " +
            "WHERE u.deleted = 0 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (u.nickname LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.username LIKE CONCAT('%', #{keyword}, '%') " +
            "OR EXISTS (SELECT 1 FROM user_address a WHERE a.user_id = u.id AND a.deleted = 0 AND a.phone LIKE CONCAT('%', #{keyword}, '%')))" +
            "</if> " +
            "ORDER BY u.create_time DESC" +
            "</script>")
    Page<SysUser> selectUserListWithPhone(Page<SysUser> page, @Param("keyword") String keyword);
}
