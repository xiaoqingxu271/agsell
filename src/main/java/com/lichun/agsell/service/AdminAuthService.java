package com.lichun.agsell.service;

import com.lichun.agsell.model.dto.AdminLoginRequest;
import com.lichun.agsell.model.vo.AdminLoginVO;
import com.lichun.agsell.model.vo.AdminInfoVO;

public interface AdminAuthService {

    /**
     * 管理员登录
     */
    AdminLoginVO login(AdminLoginRequest request);

    /**
     * 获取当前管理员信息
     */
    AdminInfoVO getAdminInfo();

    /**
     * 管理员退出登录
     */
    void logout();
}
