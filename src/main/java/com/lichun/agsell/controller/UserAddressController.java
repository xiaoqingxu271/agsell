package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.UserAddressDTO;
import com.lichun.agsell.model.vo.UserAddressVO;
import com.lichun.agsell.service.UserAddressService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "收货地址接口", description = "用户收货地址增删改查")
@RestController
@RequestMapping("/user/address")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    @Operation(summary = "地址列表")
    @GetMapping("/list")
    public BaseResponse<List<UserAddressVO>> listAddresses() {
        return ResultUtils.success(userAddressService.listAddresses());
    }

    @Operation(summary = "新增地址")
    @PostMapping
    public BaseResponse<Void> addAddress(@RequestBody UserAddressDTO dto) {
        userAddressService.addAddress(dto);
        return ResultUtils.success(null);
    }

    @Operation(summary = "更新地址")
    @PutMapping("/{id}")
    public BaseResponse<Void> updateAddress(@PathVariable Long id,
                                             @RequestBody UserAddressDTO dto) {
        dto.setId(id);
        userAddressService.updateAddress(dto);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> deleteAddress(@PathVariable Long id) {
        userAddressService.deleteAddress(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "设置默认地址")
    @PostMapping("/{id}/default")
    public BaseResponse<Void> setDefaultAddress(@PathVariable Long id) {
        userAddressService.setDefaultAddress(id);
        return ResultUtils.success(null);
    }
}
