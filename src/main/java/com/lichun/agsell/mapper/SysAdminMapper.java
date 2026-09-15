package com.lichun.agsell.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lichun.agsell.model.entity.SysAdmin;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员 Mapper
 */
@Mapper
public interface SysAdminMapper extends BaseMapper<SysAdmin> {

    /**
     * 物理删除已逻辑删除的同名管理员记录
     * 用于用户名复用：uk_username 唯一索引对逻辑删除记录仍生效，重建前需清理
     */
    @Delete("DELETE FROM sys_admin WHERE username = #{username} AND deleted = 1")
    int purgeDeletedByUsername(@Param("username") String username);
}
