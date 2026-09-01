package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.BannerMapper;
import com.lichun.agsell.model.dto.BannerCreateRequest;
import com.lichun.agsell.model.entity.Banner;
import com.lichun.agsell.model.vo.BannerVO;
import com.lichun.agsell.service.BannerService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerMapper bannerMapper;

    @Override
    public List<BannerVO> listBanners() {
        List<Banner> list = bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                .eq(Banner::getStatus, 1)
                .orderByDesc(Banner::getSort));
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<BannerVO> listAllBanners() {
        List<Banner> list = bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>().orderByDesc(Banner::getSort));
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public void createBanner(BannerCreateRequest request) {
        validateBanner(request);

        Banner banner = new Banner();
        banner.setTitle(request.getTitle());
        banner.setImage(request.getImage());
        banner.setLink(request.getLink());
        banner.setSort(request.getSort() != null ? request.getSort() : 0);
        banner.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        bannerMapper.insert(banner);
    }

    @Override
    public void updateBanner(Long id, BannerCreateRequest request) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "ID不能为空");
        Banner exist = bannerMapper.selectById(id);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "轮播图不存在");

        validateBanner(request);

        Banner update = new Banner();
        update.setId(id);
        update.setTitle(request.getTitle());
        update.setImage(request.getImage());
        update.setLink(request.getLink());
        update.setSort(request.getSort() != null ? request.getSort() : 0);
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        bannerMapper.updateById(update);
    }

    @Override
    public void deleteBanner(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "ID不能为空");
        Banner exist = bannerMapper.selectById(id);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "轮播图不存在");
        bannerMapper.deleteById(id);
    }

    // ==================== 私有方法 ====================

    private void validateBanner(BannerCreateRequest request) {
        ThrowUtils.throwIf(request.getImage() == null || request.getImage().isBlank(),
                ErrorCode.PARAMS_ERROR, "图片URL不能为空");
        ThrowUtils.throwIf(request.getTitle() != null && request.getTitle().length() > 100,
                ErrorCode.PARAMS_ERROR, "标题不能超过100个字符");
    }

    private BannerVO convertToVO(Banner banner) {
        BannerVO vo = new BannerVO();
        vo.setId(banner.getId());
        vo.setTitle(banner.getTitle());
        vo.setImage(banner.getImage());
        vo.setLink(banner.getLink());
        vo.setSort(banner.getSort());
        vo.setStatus(banner.getStatus());
        vo.setCreateTime(banner.getCreateTime());
        return vo;
    }
}
