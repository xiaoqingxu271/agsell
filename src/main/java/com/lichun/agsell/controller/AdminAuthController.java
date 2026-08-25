package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.AdminLoginRequest;
import com.lichun.agsell.model.vo.AdminInfoVO;
import com.lichun.agsell.model.vo.AdminLoginVO;
import com.lichun.agsell.service.AdminAuthService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理员接口", description = "管理员登录、获取信息")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public BaseResponse<AdminLoginVO> login(@RequestBody AdminLoginRequest request) {
        return ResultUtils.success(adminAuthService.login(request));
    }

    @Operation(summary = "获取当前管理员信息")
    @GetMapping("/info")
    public BaseResponse<AdminInfoVO> getAdminInfo() {
        return ResultUtils.success(adminAuthService.getAdminInfo());
    }

    @Operation(summary = "管理员退出登录")
    @PostMapping("/logout")
    public BaseResponse<Void> logout() {
        adminAuthService.logout();
        return ResultUtils.success(null);
    }
}
