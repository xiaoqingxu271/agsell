package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.UserLoginRequest;
import com.lichun.agsell.model.dto.UserRegisterRequest;
import com.lichun.agsell.model.dto.UserUpdateRequest;
import com.lichun.agsell.model.vo.UserInfoVO;
import com.lichun.agsell.model.vo.UserLoginVO;
import com.lichun.agsell.service.UserService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户接口", description = "小程序用户登录、注册、信息管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "小程序登录")
    @PostMapping("/login")
    public BaseResponse<UserLoginVO> login(@RequestBody UserLoginRequest request) {
        return ResultUtils.success(userService.login(request));
    }

    @Operation(summary = "手机号注册")
    @PostMapping("/register")
    public BaseResponse<UserLoginVO> register(@RequestBody UserRegisterRequest request) {
        return ResultUtils.success(userService.register(request));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public BaseResponse<UserInfoVO> getUserInfo() {
        return ResultUtils.success(userService.getUserInfo());
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/info")
    public BaseResponse<Void> updateUserInfo(@RequestBody UserUpdateRequest request) {
        userService.updateUserInfo(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public BaseResponse<Void> logout() {
        userService.logout();
        return ResultUtils.success(null);
    }
}
