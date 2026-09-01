package com.lichun.agsell.service;

import com.lichun.agsell.model.dto.BannerCreateRequest;
import com.lichun.agsell.model.vo.BannerVO;

import java.util.List;

public interface BannerService {

    /**
     * 轮播图列表（用户端，仅启用）
     */
    List<BannerVO> listBanners();

    /**
     * 轮播图列表（管理端，全部）
     */
    List<BannerVO> listAllBanners();

    /**
     * 新增轮播图
     */
    void createBanner(BannerCreateRequest request);

    /**
     * 更新轮播图
     */
    void updateBanner(Long id, BannerCreateRequest request);

    /**
     * 删除轮播图
     */
    void deleteBanner(Long id);
}
