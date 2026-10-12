package com.lichun.agsell.controller.user;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.service.ProductViewService;
import com.lichun.agsell.utils.ResultUtils;
import com.lichun.agsell.utils.UserContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端-商品浏览足迹上报
 * <p>
 * 商品详情页进入时由前端异步上报一次，作为 AI 个性化推荐的实时行为信号。
 * 接口在 JWT 白名单内（游客可调用），游客与下架商品由服务层静默跳过，
 * 任何失败都返回成功——埋点属于附加能力，不允许给用户报错。
 */
@Slf4j
@Tag(name = "用户端-浏览足迹", description = "商品详情页浏览上报（AI 个性化行为信号）")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductViewController {

    private final ProductViewService productViewService;
    private final UserContextResolver userContextResolver;

    @Operation(summary = "上报一次商品浏览")
    @PostMapping("/view")
    public BaseResponse<Boolean> recordView(@RequestParam Long productId, HttpServletRequest httpRequest) {
        Long userId = userContextResolver.resolveUserId(httpRequest);
        productViewService.recordView(userId, productId);
        return ResultUtils.success(true);
    }
}
