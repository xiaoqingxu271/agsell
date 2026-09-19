package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.annotation.OperationLog;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.*;
import com.lichun.agsell.model.vo.AdminListItemVO;
import com.lichun.agsell.model.vo.ConfigItemVO;
import com.lichun.agsell.model.vo.SysLogPageVO;
import com.lichun.agsell.service.AdminSystemService;
import com.lichun.agsell.service.SysConfigService;
import com.lichun.agsell.service.SysLogService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端-系统管理（仅超级管理员 SUPER_ADMIN 可访问，由 JwtAuthInterceptor 强制校验）
 * 管理员管理 + 系统配置 + 操作日志
 */
@Tag(name = "管理端-系统管理", description = "管理员管理、系统配置、操作日志（仅超级管理员）")
@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final AdminSystemService adminSystemService;
    private final SysConfigService sysConfigService;
    private final SysLogService sysLogService;

    // ========== 管理员管理 ==========

    @Operation(summary = "管理员列表")
    @GetMapping("/admin/list")
    public BaseResponse<Page<AdminListItemVO>> listAdmins(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ResultUtils.success(adminSystemService.listAdmins(pageNum, pageSize, keyword));
    }

    @Operation(summary = "新增管理员")
    @PostMapping("/admin")
    @OperationLog(module = "管理员管理", action = "新增管理员",
            content = "新增了管理员「#{#request.username}」")
    public BaseResponse<Void> createAdmin(@RequestBody AdminCreateRequest request) {
        adminSystemService.createAdmin(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "编辑管理员")
    @PutMapping("/admin/{id}")
    @OperationLog(module = "管理员管理", action = "编辑管理员",
            content = "编辑了管理员 #{#id}")
    public BaseResponse<Void> updateAdmin(@PathVariable Long id, @RequestBody AdminUpdateRequest request) {
        adminSystemService.updateAdmin(id, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/admin/{id}/password")
    @OperationLog(module = "管理员管理", action = "重置密码",
            content = "重置了管理员 #{#id} 的密码")
    public BaseResponse<Void> resetPassword(@PathVariable Long id, @RequestBody AdminPasswordResetRequest request) {
        adminSystemService.resetPassword(id, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "启用/禁用管理员")
    @PutMapping("/admin/{id}/status")
    @OperationLog(module = "管理员管理", action = "启停管理员",
            content = "管理员 #{#id} 已#{#status == 1 ? '启用' : '禁用'}")
    public BaseResponse<Void> updateStatus(@PathVariable Long id, @RequestParam int status) {
        adminSystemService.updateStatus(id, status);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除管理员")
    @DeleteMapping("/admin/{id}")
    @OperationLog(module = "管理员管理", action = "删除管理员",
            content = "删除了管理员 #{#id}")
    public BaseResponse<Void> deleteAdmin(@PathVariable Long id) {
        adminSystemService.deleteAdmin(id);
        return ResultUtils.success(null);
    }

    // ========== 系统配置 ==========

    @Operation(summary = "系统配置列表")
    @GetMapping("/config/list")
    public BaseResponse<List<ConfigItemVO>> listConfigs() {
        return ResultUtils.success(sysConfigService.listConfigs());
    }

    @Operation(summary = "更新系统配置")
    @PutMapping("/config")
    @OperationLog(module = "系统配置", action = "更新配置",
            content = "更新了系统配置（共 #{#request.items.size()} 项）")
    public BaseResponse<Void> updateConfigs(@RequestBody ConfigUpdateRequest request) {
        sysConfigService.updateConfigs(request);
        return ResultUtils.success(null);
    }

    // ========== 操作日志 ==========

    @Operation(summary = "操作日志列表")
    @GetMapping("/log/list")
    public BaseResponse<SysLogPageVO> listLogs(SysLogQueryRequest request) {
        return ResultUtils.success(sysLogService.listLogs(request));
    }
}
