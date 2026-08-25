package com.lichun.agsell.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.utils.ThrowUtils;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SysUserAddressMapper;
import com.lichun.agsell.model.dto.UserAddressDTO;
import com.lichun.agsell.model.entity.SysUserAddress;
import com.lichun.agsell.model.vo.UserAddressVO;
import com.lichun.agsell.service.UserAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl extends ServiceImpl<SysUserAddressMapper, SysUserAddress> implements UserAddressService {

    @Override
    public List<UserAddressVO> listAddresses() {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        List<SysUserAddress> list = lambdaQuery()
                .eq(SysUserAddress::getUserId, userId)
                .orderByDesc(SysUserAddress::getIsDefault)
                .orderByDesc(SysUserAddress::getCreateTime)
                .list();

        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addAddress(UserAddressDTO dto) {
        validateAddress(dto);
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        // 若设为默认地址，先清除该用户其他默认地址
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            lambdaUpdate()
                    .eq(SysUserAddress::getUserId, userId)
                    .set(SysUserAddress::getIsDefault, 0)
                    .update();
        }

        SysUserAddress address = new SysUserAddress();
        address.setUserId(userId);
        address.setReceiver(dto.getReceiver());
        address.setPhone(dto.getPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetail(dto.getDetail());
        address.setIsDefault(dto.getIsDefault());
        address.setTag(dto.getTag());
        save(address);
    }

    @Override
    @Transactional
    public void updateAddress(UserAddressDTO dto) {
        ThrowUtils.throwIf(dto.getId() == null, ErrorCode.PARAMS_ERROR, "地址ID不能为空");
        validateAddress(dto);

        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        // 校验归属
        SysUserAddress exist = lambdaQuery()
                .eq(SysUserAddress::getId, dto.getId())
                .eq(SysUserAddress::getUserId, userId)
                .one();
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "地址不存在或无权限");

        // 若设为默认地址，先清除该用户其他默认地址
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            lambdaUpdate()
                    .eq(SysUserAddress::getUserId, userId)
                    .ne(SysUserAddress::getId, dto.getId())
                    .set(SysUserAddress::getIsDefault, 0)
                    .update();
        }

        SysUserAddress update = new SysUserAddress();
        update.setId(dto.getId());
        update.setReceiver(dto.getReceiver());
        update.setPhone(dto.getPhone());
        update.setProvince(dto.getProvince());
        update.setCity(dto.getCity());
        update.setDistrict(dto.getDistrict());
        update.setDetail(dto.getDetail());
        update.setIsDefault(dto.getIsDefault());
        update.setTag(dto.getTag());
        updateById(update);
    }

    @Override
    public void deleteAddress(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "地址ID不能为空");
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        boolean exists = lambdaQuery()
                .eq(SysUserAddress::getId, id)
                .eq(SysUserAddress::getUserId, userId)
                .exists();
        ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "地址不存在或无权限");

        removeById(id);
    }

    @Override
    @Transactional
    public void setDefaultAddress(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "地址ID不能为空");
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        // 校验归属
        SysUserAddress exist = lambdaQuery()
                .eq(SysUserAddress::getId, id)
                .eq(SysUserAddress::getUserId, userId)
                .one();
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "地址不存在或无权限");

        // 先清除所有默认
        lambdaUpdate()
                .eq(SysUserAddress::getUserId, userId)
                .set(SysUserAddress::getIsDefault, 0)
                .update();

        // 再设置目标为默认
        lambdaUpdate()
                .eq(SysUserAddress::getId, id)
                .set(SysUserAddress::getIsDefault, 1)
                .update();
    }

    // ==================== 私有方法 ====================

    private void validateAddress(UserAddressDTO dto) {
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getReceiver()), ErrorCode.PARAMS_ERROR, "收件人不能为空");
        ThrowUtils.throwIf(dto.getReceiver().length() > 20, ErrorCode.PARAMS_ERROR, "收件人姓名不能超过20个字符");
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getPhone()), ErrorCode.PARAMS_ERROR, "手机号不能为空");
        ThrowUtils.throwIf(!isMobile(dto.getPhone()), ErrorCode.PARAMS_ERROR, "手机号格式不正确");
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getProvince()), ErrorCode.PARAMS_ERROR, "省不能为空");
        ThrowUtils.throwIf(dto.getProvince().length() > 32, ErrorCode.PARAMS_ERROR, "省份长度不能超过32个字符");
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getCity()), ErrorCode.PARAMS_ERROR, "市不能为空");
        ThrowUtils.throwIf(dto.getCity().length() > 32, ErrorCode.PARAMS_ERROR, "城市长度不能超过32个字符");
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getDistrict()), ErrorCode.PARAMS_ERROR, "区县不能为空");
        ThrowUtils.throwIf(dto.getDistrict().length() > 32, ErrorCode.PARAMS_ERROR, "区县长度不能超过32个字符");
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getDetail()), ErrorCode.PARAMS_ERROR, "详细地址不能为空");
        ThrowUtils.throwIf(dto.getDetail().length() > 100, ErrorCode.PARAMS_ERROR, "详细地址长度不能超过100个字符");
        ThrowUtils.throwIf(dto.getTag() != null && dto.getTag().length() > 20,
                ErrorCode.PARAMS_ERROR, "地址标签长度不能超过20个字符");
    }

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private boolean isMobile(String phone) {
        return phone != null && MOBILE_PATTERN.matcher(phone).matches();
    }

    private UserAddressVO convertToVO(SysUserAddress address) {
        UserAddressVO vo = new UserAddressVO();
        vo.setId(address.getId());
        vo.setReceiver(address.getReceiver());
        vo.setPhone(address.getPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetail(address.getDetail());
        vo.setFullAddress(address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        vo.setTag(address.getTag());
        vo.setCreateTime(address.getCreateTime());
        return vo;
    }
}
