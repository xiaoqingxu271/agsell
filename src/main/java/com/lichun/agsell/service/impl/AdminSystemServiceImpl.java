package com.lichun.agsell.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SysAdminMapper;
import com.lichun.agsell.model.dto.AdminCreateRequest;
import com.lichun.agsell.model.dto.AdminPasswordResetRequest;
import com.lichun.agsell.model.dto.AdminUpdateRequest;
import com.lichun.agsell.model.entity.SysAdmin;
import com.lichun.agsell.model.vo.AdminListItemVO;
import com.lichun.agsell.service.AdminSystemService;
import com.lichun.agsell.service.RedisTokenService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 管理员管理实现（RBAC 三级：SUPER_ADMIN → ADMIN → OPERATOR）
 * 保护规则：
 * R1 不可操作自己；R2 至少保留一个超级管理员；R3 权限单向；R4 角色不越权授予
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSystemServiceImpl implements AdminSystemService {

    private final SysAdminMapper adminMapper;
    private final RedisTokenService redisTokenService;

    /** 可创建/授予的角色（超级管理员保持唯一，不可授予） */
    private static final Set<String> CREATABLE_ROLES = Set.of("ADMIN", "OPERATOR");

    @Override
    public Page<AdminListItemVO> listAdmins(int pageNum, int pageSize, String keyword) {
        Page<SysAdmin> page = adminMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysAdmin>()
                        .and(StringUtils.hasText(keyword), w -> w.like(SysAdmin::getUsername, keyword)
                                .or().like(SysAdmin::getRealName, keyword))
                        .orderByDesc(SysAdmin::getCreateTime));
        Page<AdminListItemVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    @Transactional
    public void createAdmin(AdminCreateRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(request.getUsername()), ErrorCode.PARAMS_ERROR, "用户名不能为空");
        ThrowUtils.throwIf(request.getUsername().length() > 32, ErrorCode.PARAMS_ERROR, "用户名长度不能超过32个字符");
        ThrowUtils.throwIf(!StringUtils.hasText(request.getPassword()), ErrorCode.PARAMS_ERROR, "密码不能为空");
        ThrowUtils.throwIf(request.getPassword().length() < 6, ErrorCode.PARAMS_ERROR, "密码长度不能少于6位");
        ThrowUtils.throwIf(!CREATABLE_ROLES.contains(request.getRole()), ErrorCode.PARAMS_ERROR, "非法角色，仅允许 ADMIN/OPERATOR");

        // 2. 用户名唯一（先物理清理同名逻辑删除记录，支持用户名复用；再校验活动记录）
        adminMapper.purgeDeletedByUsername(request.getUsername());
        Long exists = adminMapper.selectCount(new LambdaQueryWrapper<SysAdmin>()
                .eq(SysAdmin::getUsername, request.getUsername()));
        ThrowUtils.throwIf(exists > 0, ErrorCode.OPERATION_ERROR, "用户名已存在");

        // 3. 创建（密码 BCrypt 加密）
        SysAdmin admin = new SysAdmin();
        admin.setUsername(request.getUsername());
        admin.setPassword(BCrypt.hashpw(request.getPassword()));
        admin.setRealName(request.getRealName());
        admin.setRole(request.getRole());
        admin.setStatus(1);
        adminMapper.insert(admin);
        log.info("[AdminSystem] 新增管理员, username={}, role={}, operator={}", request.getUsername(),
                request.getRole(), AdminContext.getCurrentAdminId());
    }

    @Override
    @Transactional
    public void updateAdmin(Long id, AdminUpdateRequest request) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "管理员ID不能为空");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        SysAdmin target = getAdminOrThrow(id);
        // R1：不可操作自己（禁止修改自己的角色）
        ThrowUtils.throwIf(id.equals(AdminContext.getCurrentAdminId()), ErrorCode.OPERATION_ERROR, "不能修改当前登录账号");
        // R4：目标角色不越权授予
        ThrowUtils.throwIf(!CREATABLE_ROLES.contains(request.getRole()), ErrorCode.PARAMS_ERROR, "非法角色，仅允许 ADMIN/OPERATOR");

        SysAdmin update = new SysAdmin();
        update.setId(id);
        if (request.getRealName() != null) {
            update.setRealName(request.getRealName());
        }
        if (request.getRole() != null) {
            update.setRole(request.getRole());
        }
        adminMapper.updateById(update);
        log.info("[AdminSystem] 编辑管理员, id={}, operator={}", id, AdminContext.getCurrentAdminId());
    }

    @Override
    @Transactional
    public void resetPassword(Long id, AdminPasswordResetRequest request) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "管理员ID不能为空");
        ThrowUtils.throwIf(request == null || !StringUtils.hasText(request.getNewPassword()),
                ErrorCode.PARAMS_ERROR, "新密码不能为空");
        ThrowUtils.throwIf(request.getNewPassword().length() < 6, ErrorCode.PARAMS_ERROR, "密码长度不能少于6位");
        getAdminOrThrow(id);

        SysAdmin update = new SysAdmin();
        update.setId(id);
        update.setPassword(BCrypt.hashpw(request.getNewPassword()));
        adminMapper.updateById(update);
        log.info("[AdminSystem] 重置密码, id={}, operator={}", id, AdminContext.getCurrentAdminId());
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "管理员ID不能为空");
        ThrowUtils.throwIf(status == null || (status != 0 && status != 1), ErrorCode.PARAMS_ERROR, "状态值不合法");
        SysAdmin target = getAdminOrThrow(id);
        // R1：不可禁用自己
        ThrowUtils.throwIf(id.equals(AdminContext.getCurrentAdminId()), ErrorCode.OPERATION_ERROR, "不能禁用当前登录账号");
        // R2：禁用超级管理员前至少保留一个
        if ("SUPER_ADMIN".equals(target.getRole()) && status == 0) {
            checkSuperAdminCount();
        }
        SysAdmin update = new SysAdmin();
        update.setId(id);
        update.setStatus(status);
        adminMapper.updateById(update);
        // 禁用：立即踢下线（删除该管理员全部 Redis token）
        if (status == 0) {
            redisTokenService.deleteAdminTokens(id);
        }
        log.info("[AdminSystem] 启停管理员, id={}, status={}, operator={}", id, status, AdminContext.getCurrentAdminId());
    }

    @Override
    @Transactional
    public void deleteAdmin(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "管理员ID不能为空");
        SysAdmin target = getAdminOrThrow(id);
        // R1：不可删除自己
        ThrowUtils.throwIf(id.equals(AdminContext.getCurrentAdminId()), ErrorCode.OPERATION_ERROR, "不能删除当前登录账号");
        // R2：删除超级管理员前至少保留一个
        if ("SUPER_ADMIN".equals(target.getRole())) {
            checkSuperAdminCount();
        }
        // R3：防御性校验——目标角色不得高于操作者（当前实现拦截器已保证仅 SUPER_ADMIN 可达）
        ThrowUtils.throwIf(roleLevel(target.getRole()) >= roleLevel(AdminContext.getCurrentRole()),
                ErrorCode.ADMIN_NO_AUTH_ERROR, "无权操作该管理员");
        adminMapper.deleteById(id); // 逻辑删除
        log.info("[AdminSystem] 删除管理员, id={}, operator={}", id, AdminContext.getCurrentAdminId());
    }

    // ========== 私有方法 ==========

    private SysAdmin getAdminOrThrow(Long id) {
        SysAdmin admin = adminMapper.selectById(id);
        ThrowUtils.throwIf(admin == null, ErrorCode.NOT_FOUND_ERROR, "管理员不存在");
        return admin;
    }

    /** R2：校验系统中正常状态的超级管理员数量 > 1，否则拒绝 */
    private void checkSuperAdminCount() {
        Long superCount = adminMapper.selectCount(new LambdaQueryWrapper<SysAdmin>()
                .eq(SysAdmin::getRole, "SUPER_ADMIN")
                .eq(SysAdmin::getStatus, 1));
        ThrowUtils.throwIf(superCount <= 1, ErrorCode.OPERATION_ERROR, "至少保留一个超级管理员");
    }

    private int roleLevel(String role) {
        if ("SUPER_ADMIN".equals(role)) {
            return 2;
        }
        if ("ADMIN".equals(role)) {
            return 1;
        }
        return 0;
    }

    private AdminListItemVO toVO(SysAdmin admin) {
        AdminListItemVO vo = new AdminListItemVO();
        vo.setId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setRealName(admin.getRealName());
        vo.setRole(admin.getRole());
        vo.setStatus(admin.getStatus());
        vo.setLoginIp(admin.getLoginIp());
        vo.setLoginTime(admin.getLoginTime());
        vo.setCreateTime(admin.getCreateTime());
        return vo;
    }
}
