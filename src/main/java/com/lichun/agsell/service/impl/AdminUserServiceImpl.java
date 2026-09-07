package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.utils.ThrowUtils;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.AdminUserStatusRequest;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.vo.AdminUserListItemVO;
import com.lichun.agsell.service.AdminUserService;
import com.lichun.agsell.service.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final SysUserMapper sysUserMapper;
    private final RedisTokenService redisTokenService;

    @Override
    public Page<AdminUserListItemVO> listUsers(int pageNum, int pageSize, String keyword) {
        Page<SysUser> page = sysUserMapper.selectUserListWithPhone(new Page<>(pageNum, pageSize), keyword);

        List<AdminUserListItemVO> list = page.getRecords().stream()
                .map(this::convertToListItemVO)
                .collect(Collectors.toList());

        Page<AdminUserListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(list);
        return result;
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, AdminUserStatusRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(request.getStatus() == null, ErrorCode.PARAMS_ERROR, "状态不能为空");
        ThrowUtils.throwIf(request.getStatus() != 0 && request.getStatus() != 1,
                ErrorCode.PARAMS_ERROR, "状态值不合法");

        SysUser exist = sysUserMapper.selectById(userId);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        SysUser user = new SysUser();
        user.setId(userId);
        user.setStatus(request.getStatus());
        sysUserMapper.updateById(user);

        // 禁用用户时立即删除其全部登录 token，踢下线
        if (request.getStatus() == 0) {
            redisTokenService.deleteUserTokens(userId);
            log.info("管理员禁用用户并清除登录态，userId: {}", userId);
        } else {
            log.info("管理员启用用户，userId: {}", userId);
        }
    }

    private AdminUserListItemVO convertToListItemVO(SysUser user) {
        AdminUserListItemVO vo = new AdminUserListItemVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setLoginTime(user.getLoginTime());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
