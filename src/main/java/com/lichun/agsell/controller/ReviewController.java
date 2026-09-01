package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.ReviewCreateRequest;
import com.lichun.agsell.model.vo.ReviewMyVO;
import com.lichun.agsell.model.vo.ReviewVO;
import com.lichun.agsell.service.ReviewService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户端-评价接口", description = "提交评价、查看评价列表")
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "提交评价")
    @PostMapping
    public BaseResponse<Void> createReview(@RequestBody ReviewCreateRequest request) {
        reviewService.createReview(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "商品评价列表")
    @GetMapping("/product/{productId}")
    public BaseResponse<Page<ReviewVO>> listProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(reviewService.listProductReviews(productId, pageNum, pageSize));
    }

    @Operation(summary = "我的评价")
    @GetMapping("/my")
    public BaseResponse<Page<ReviewMyVO>> listMyReviews(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(reviewService.listMyReviews(pageNum, pageSize));
    }
}
