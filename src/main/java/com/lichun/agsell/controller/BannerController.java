package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.BannerCreateRequest;
import com.lichun.agsell.model.vo.BannerVO;
import com.lichun.agsell.service.BannerService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "轮播图接口", description = "用户端浏览 + 管理端CRUD")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @Operation(summary = "轮播图列表（用户端）")
    @GetMapping("/banner/list")
    public BaseResponse<List<BannerVO>> listBanners() {
        return ResultUtils.success(bannerService.listBanners());
    }

    @Operation(summary = "轮播图列表（管理端）")
    @GetMapping("/admin/banner/list")
    public BaseResponse<List<BannerVO>> listAllBanners() {
        return ResultUtils.success(bannerService.listAllBanners());
    }

    @Operation(summary = "新增轮播图")
    @PostMapping("/admin/banner")
    public BaseResponse<Void> createBanner(@RequestBody BannerCreateRequest request) {
        bannerService.createBanner(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "更新轮播图")
    @PutMapping("/admin/banner/{id}")
    public BaseResponse<Void> updateBanner(@PathVariable Long id,
                                            @RequestBody BannerCreateRequest request) {
        bannerService.updateBanner(id, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除轮播图")
    @DeleteMapping("/admin/banner/{id}")
    public BaseResponse<Void> deleteBanner(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ResultUtils.success(null);
    }
}
