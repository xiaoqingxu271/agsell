package com.lichun.agsell.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.utils.ThrowUtils;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.UserLoginRequest;
import com.lichun.agsell.model.dto.UserRegisterRequest;
import com.lichun.agsell.model.dto.UserUpdateRequest;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.vo.UserInfoVO;
import com.lichun.agsell.model.vo.UserLoginVO;
import com.lichun.agsell.service.UserService;
import com.lichun.agsell.service.RedisTokenService;
import com.lichun.agsell.service.SmsCodeService;
import com.lichun.agsell.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    private final JwtUtils jwtUtils;
    private final SmsCodeService smsCodeService;
    private final RedisTokenService redisTokenService;

    @Override
    @Transactional
    public UserLoginVO login(UserLoginRequest request) {
        ThrowUtils.throwIf(request.getCode() == null || request.getCode().isBlank(),
                ErrorCode.PARAMS_ERROR, "登录code不能为空");

        // 1. 调用微信接口换取 openid（开发阶段使用 mock）
        String openid = mockOpenid(request.getCode());

        // 2. 查询用户
        SysUser user = lambdaQuery()
                .eq(SysUser::getOpenid, openid)
                .one();

        // 3. 自动注册（首次登录）
        if (user == null) {
            user = new SysUser();
            user.setOpenid(openid);
            user.setUsername(generateUsername(openid));
            user.setPassword(""); // 微信登录用户无密码
            user.setNickname("微信用户_" + openid.substring(Math.min(4, openid.length())));
            user.setStatus(1);
            save(user);
            log.info("新用户自动注册成功，openid: {}, userId: {}", openid, user.getId());
        }

        // 4. 校验状态
        ThrowUtils.throwIf(user.getStatus() == 0, ErrorCode.NO_AUTH_ERROR, "账号已被禁用");

        // 5. 更新登录信息
        user.setLoginIp("127.0.0.1"); // TODO: 实际获取客户端IP
        user.setLoginTime(LocalDateTime.now());
        updateById(user);

        // 6. 生成 token
        String token = jwtUtils.generateToken(user.getId());
        String jti = jwtUtils.getJti(token);
        // 保存到 Redis（过期时间与 JWT 一致）
        redisTokenService.saveUserToken(user.getId(), token, jti, jwtUtils.getExpirationSeconds());

        // 7. 构建响应
        UserLoginVO vo = new UserLoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        return vo;
    }

    @Override
    @Transactional
    public UserLoginVO register(UserRegisterRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(request.getPhone() == null || request.getPhone().isBlank(),
                ErrorCode.PARAMS_ERROR, "手机号不能为空");
        ThrowUtils.throwIf(!isMobile(request.getPhone()),
                ErrorCode.PARAMS_ERROR, "手机号格式不正确");
        ThrowUtils.throwIf(request.getPassword() == null || request.getPassword().isBlank(),
                ErrorCode.PARAMS_ERROR, "密码不能为空");
        ThrowUtils.throwIf(request.getPassword().length() < 6,
                ErrorCode.PARAMS_ERROR, "密码长度不能少于6位");
        ThrowUtils.throwIf(request.getNickname() != null && request.getNickname().length() > 20,
                ErrorCode.PARAMS_ERROR, "昵称长度不能超过20个字符");

        // 2. 验证码校验
        ThrowUtils.throwIf(!smsCodeService.verifyCode(request.getPhone(), request.getCode()),
                ErrorCode.SMS_CODE_INVALID, "验证码错误");

        // 3. 检查手机号是否已注册
        long count = lambdaQuery()
                .eq(SysUser::getUsername, request.getPhone())
                .count();
        ThrowUtils.throwIf(count > 0, ErrorCode.USER_ALREADY_EXISTS, "该手机号已注册");

        // 4. 创建用户
        SysUser user = new SysUser();
        user.setUsername(request.getPhone());
        user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setNickname(StrUtil.isNotBlank(request.getNickname())
                ? request.getNickname() : "用户_" + request.getPhone().substring(7));
        user.setPhone(request.getPhone());
        user.setStatus(1);
        save(user);

        // 6. 生成 token
        String token = jwtUtils.generateToken(user.getId());
        String jti = jwtUtils.getJti(token);
        // 保存到 Redis（过期时间与 JWT 一致）
        redisTokenService.saveUserToken(user.getId(), token, jti, jwtUtils.getExpirationSeconds());

        // 7. 构建响应
        UserLoginVO vo = new UserLoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        return vo;
    }

    @Override
    public UserInfoVO getUserInfo() {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        SysUser user = getById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setHasPhone(StrUtil.isNotBlank(user.getPhone()));
        return vo;
    }

    @Override
    @Transactional
    public void updateUserInfo(UserUpdateRequest request) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        SysUser user = getById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 昵称长度校验
        ThrowUtils.throwIf(request.getNickname() != null && request.getNickname().length() > 20,
                ErrorCode.PARAMS_ERROR, "昵称长度不能超过20个字符");

        // 如果修改了手机号，检查是否已被其他用户注册
        if (request.getPhone() != null && !request.getPhone().equals(user.getUsername())) {
            ThrowUtils.throwIf(!isMobile(request.getPhone()),
                    ErrorCode.PARAMS_ERROR, "手机号格式不正确");
            long count = lambdaQuery()
                    .eq(SysUser::getUsername, request.getPhone())
                    .count();
            ThrowUtils.throwIf(count > 0, ErrorCode.PHONE_ALREADY_EXISTS, "该手机号已被注册");
            user.setUsername(request.getPhone());
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setNickname(request.getNickname());
        update.setAvatar(request.getAvatar());
        if (request.getPhone() != null) {
            update.setPhone(request.getPhone());
        }
        updateById(update);
    }

    @Override
    public void logout() {
        Long userId = BaseContext.getCurrentId();
        String jti = BaseContext.getCurrentJti();
        if (userId != null && jti != null) {
            redisTokenService.deleteUserToken(userId, jti);
            log.info("[Logout] 用户退出登录，删除 Redis token, userId={}, jti={}", userId, jti);
        }
        BaseContext.removeCurrentId();
    }

    // ==================== 私有方法 ====================

    /** 中国大陆手机号正则 */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private boolean isMobile(String phone) {
        return phone != null && MOBILE_PATTERN.matcher(phone).matches();
    }

    /**
     * 获取 openid（开发阶段 mock，生产环境调用微信接口）
     */
    private String mockOpenid(String code) {
        // TODO: 生产环境替换为真实微信接口调用
        // GET https://api.weixin.qq.com/sns/jscode2session?appid=...&secret=...&js_code=...&grant_type=authorization_code
        log.info("微信登录 code: {}, 开发模式使用 mock openid", code);
        return "mock_openid_" + Math.abs(code != null ? code.hashCode() : 0);
    }

    /**
     * 生成唯一用户名
     */
    private String generateUsername(String openid) {
        return "wx_" + openid.hashCode() + "_" + System.currentTimeMillis() % 10000;
    }
}
