package com.lichun.agsell.controller.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.vo.FavoriteItemVO;
import com.lichun.agsell.service.FavoriteService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端-商品收藏
 * <p>
 * 接口不在 JWT 白名单内，未登录由拦截器统一拒绝（前端按钮先静默登录）。
 * 收藏数据同时是 AI 个性化偏好的一路信号（ai-service 只读聚合）。
 */
@Tag(name = "用户端-收藏", description = "商品收藏：加/取消/列表/状态")
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏商品（幂等）")
    @PostMapping("/{productId}")
    public BaseResponse<Boolean> add(@PathVariable Long productId) {
        favoriteService.add(productId);
        return ResultUtils.success(true);
    }

    @Operation(summary = "取消收藏（幂等）")
    @DeleteMapping("/{productId}")
    public BaseResponse<Boolean> remove(@PathVariable Long productId) {
        return ResultUtils.success(favoriteService.remove(productId));
    }

    @Operation(summary = "我的收藏分页（收藏时间倒序）")
    @GetMapping("/list")
    public BaseResponse<Page<FavoriteItemVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(favoriteService.pageMine(pageNum, pageSize));
    }

    @Operation(summary = "是否已收藏")
    @GetMapping("/exists")
    public BaseResponse<Boolean> exists(@RequestParam Long productId) {
        return ResultUtils.success(favoriteService.exists(productId));
    }

    @Operation(summary = "我的收藏总数")
    @GetMapping("/count")
    public BaseResponse<Long> count() {
        return ResultUtils.success(favoriteService.countMine());
    }
}
