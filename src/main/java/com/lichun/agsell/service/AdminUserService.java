package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.AdminUserStatusRequest;
import com.lichun.agsell.model.vo.AdminUserListItemVO;

public interface AdminUserService {

    /**
     * 分页查询用户列表
     */
    Page<AdminUserListItemVO> listUsers(int pageNum, int pageSize, String keyword);

    /**
     * 禁用/启用用户
     */
    void updateUserStatus(Long userId, AdminUserStatusRequest request);
}
