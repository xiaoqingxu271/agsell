package com.lichun.agsell.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SysAdminMapper;
import com.lichun.agsell.model.dto.AdminLoginRequest;
import com.lichun.agsell.model.entity.SysAdmin;
import com.lichun.agsell.model.vo.AdminInfoVO;
import com.lichun.agsell.model.vo.AdminLoginVO;
import com.lichun.agsell.service.AdminAuthService;
import com.lichun.agsell.service.RedisTokenService;
import com.lichun.agsell.utils.JwtUtils;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private final SysAdminMapper adminMapper;
    private final JwtUtils jwtUtils;
    private final RedisTokenService redisTokenService;

    @Override
    @Transactional
    public AdminLoginVO login(AdminLoginRequest request) {
        log.info("[AdminLogin] 开始登录, username={}", request.getUsername());
        // 1. 参数校验
        ThrowUtils.throwIf(request.getUsername() == null || request.getUsername().isBlank(),
                ErrorCode.PARAMS_ERROR, "用户名不能为空");
        ThrowUtils.throwIf(request.getUsername().length() > 32,
                ErrorCode.PARAMS_ERROR, "用户名长度不能超过32个字符");
        ThrowUtils.throwIf(request.getPassword() == null || request.getPassword().isBlank(),
                ErrorCode.PARAMS_ERROR, "密码不能为空");
        ThrowUtils.throwIf(request.getPassword().length() < 6,
                ErrorCode.PARAMS_ERROR, "密码长度不能少于6位");

        // 2. 查询管理员
        SysAdmin admin = adminMapper.selectOne(new LambdaQueryWrapper<SysAdmin>()
                .eq(SysAdmin::getUsername, request.getUsername()));
        ThrowUtils.throwIf(admin == null, ErrorCode.NOT_FOUND_ERROR, "用户名或密码错误");

        // 3. 密码校验（统一提示，防枚举）
        if (!BCrypt.checkpw(request.getPassword(), admin.getPassword())) {
            log.warn("[AdminLogin] 密码错误, username={}", request.getUsername());
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户名或密码错误");
        }

        // 4. 状态校验
        ThrowUtils.throwIf(admin.getStatus() == 0, ErrorCode.NO_AUTH_ERROR, "账号已被禁用");

        // 5. 更新登录信息
        admin.setLoginIp("127.0.0.1"); // TODO: 实际获取客户端IP
        admin.setLoginTime(LocalDateTime.now());
        adminMapper.updateById(admin);

        // 6. 生成 token
        String token = jwtUtils.generateAdminToken(admin.getId(), admin.getRole());
        String jti = jwtUtils.getJti(token);
        // 保存到 Redis（过期时间与 JWT 一致）
        redisTokenService.saveAdminToken(admin.getId(), token, jti, jwtUtils.getExpirationSeconds());
        log.info("[AdminLogin] 登录成功, adminId={}, token前30={}", admin.getId(), token.substring(0, Math.min(30, token.length())));

        // 7. 构建响应
        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(token);
        vo.setAdminId(admin.getId());
        vo.setRealName(admin.getRealName());
        vo.setRole(admin.getRole());
        return vo;
    }

    @Override
    public AdminInfoVO getAdminInfo() {
        Long adminId = AdminContext.getCurrentAdminId();
        log.info("[AdminInfo] getCurrentAdminId={}, thread={}", adminId, Thread.currentThread().getName());
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);

        SysAdmin admin = adminMapper.selectById(adminId);
        ThrowUtils.throwIf(admin == null, ErrorCode.NOT_FOUND_ERROR, "管理员不存在");

        AdminInfoVO vo = new AdminInfoVO();
        vo.setAdminId(admin.getId());
        vo.setRealName(admin.getRealName());
        vo.setRole(admin.getRole());
        return vo;
    }

    @Override
    public void logout() {
        Long adminId = AdminContext.getCurrentAdminId();
        String jti = AdminContext.getCurrentJti();
        if (adminId != null && jti != null) {
            redisTokenService.deleteAdminToken(adminId, jti);
            log.info("[AdminLogout] 管理员退出登录，删除 Redis token, adminId={}, jti={}", adminId, jti);
        }
        AdminContext.removeCurrentAdmin();
    }
}
