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

    @Override
    public Page<AdminUserListItemVO> listUsers(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getPhone, keyword)
                    .or().like(SysUser::getUsername, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

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

        SysUser user = new SysUser();
        user.setId(userId);
        user.setStatus(request.getStatus());
        sysUserMapper.updateById(user);
        log.info("管理员修改用户状态成功，userId: {}, status: {}", userId, request.getStatus());
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
