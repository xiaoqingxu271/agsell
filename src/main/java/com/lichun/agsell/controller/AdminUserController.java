package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.AdminUserStatusRequest;
import com.lichun.agsell.model.vo.AdminUserListItemVO;
import com.lichun.agsell.service.AdminUserService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-用户管理", description = "用户列表查询、禁用/启用")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户列表")
    @GetMapping("/list")
    public BaseResponse<Page<AdminUserListItemVO>> listUsers(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ResultUtils.success(adminUserService.listUsers(pageNum, pageSize, keyword));
    }

    @Operation(summary = "禁用/启用用户")
    @PutMapping("/{id}/status")
    public BaseResponse<Void> updateUserStatus(@PathVariable Long id,
                                                @RequestBody AdminUserStatusRequest request) {
        adminUserService.updateUserStatus(id, request);
        return ResultUtils.success(null);
    }
}
