package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lichun.agsell.model.dto.UserLoginRequest;
import com.lichun.agsell.model.dto.UserRegisterRequest;
import com.lichun.agsell.model.dto.UserUpdateRequest;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.vo.UserLoginVO;
import com.lichun.agsell.model.vo.UserInfoVO;

public interface UserService extends IService<SysUser> {

    /**
     * 微信小程序登录（code 换取 openid，自动注册/登录）
     */
    UserLoginVO login(UserLoginRequest request);

    /**
     * 手机号注册
     */
    UserLoginVO register(UserRegisterRequest request);

    /**
     * 获取当前用户信息
     */
    UserInfoVO getUserInfo();

    /**
     * 更新个人信息
     */
    void updateUserInfo(UserUpdateRequest request);

    /**
     * 退出登录（前端清除 token 即可，后端无需额外操作）
     */
    void logout();
}
