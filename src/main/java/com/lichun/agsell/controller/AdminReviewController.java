package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.ReplyRequest;
import com.lichun.agsell.model.vo.ReviewListItemVO;
import com.lichun.agsell.service.AdminReviewService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-评价管理", description = "评价列表、回复、删除")
@RestController
@RequestMapping("/admin/review")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @Operation(summary = "评价列表")
    @GetMapping("/list")
    public BaseResponse<Page<ReviewListItemVO>> listReviews(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer replied) {
        return ResultUtils.success(adminReviewService.listReviews(pageNum, pageSize, productId, replied));
    }

    @Operation(summary = "回复评价")
    @PostMapping("/{id}/reply")
    public BaseResponse<Void> replyReview(@PathVariable Long id,
                                           @RequestBody ReplyRequest request) {
        adminReviewService.replyReview(id, request.getReplyContent());
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除评价")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> deleteReview(@PathVariable Long id) {
        adminReviewService.deleteReview(id);
        return ResultUtils.success(null);
    }
}
