package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lichun.agsell.model.dto.UserAddressDTO;
import com.lichun.agsell.model.entity.SysUserAddress;
import com.lichun.agsell.model.vo.UserAddressVO;

import java.util.List;

public interface UserAddressService extends IService<SysUserAddress> {

    /**
     * 获取当前用户地址列表
     */
    List<UserAddressVO> listAddresses();

    /**
     * 新增地址
     */
    void addAddress(UserAddressDTO dto);

    /**
     * 更新地址
     */
    void updateAddress(UserAddressDTO dto);

    /**
     * 删除地址
     */
    void deleteAddress(Long id);

    /**
     * 设置默认地址
     */
    void setDefaultAddress(Long id);
}
