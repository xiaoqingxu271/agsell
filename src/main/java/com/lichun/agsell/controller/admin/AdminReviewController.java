package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.ReplyRequest;
import com.lichun.agsell.model.vo.ReviewListItemVO;
import com.lichun.agsell.model.vo.ReviewSentimentSyncVO;
import com.lichun.agsell.model.vo.ReviewSummaryVO;
import com.lichun.agsell.service.AdminReviewService;
import com.lichun.agsell.service.ReviewSentimentService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-评价管理", description = "评价列表、回复、删除、口碑分析")
@RestController
@RequestMapping("/admin/review")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;
    private final ReviewSentimentService reviewSentimentService;

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

    @Operation(summary = "口碑分析同步", description = "拉取未分析评价调用 AI 服务批量分析并回写（每次最多 100 条）")
    @PostMapping("/sentiment/sync")
    public BaseResponse<ReviewSentimentSyncVO> syncSentiment(
            @RequestParam(required = false) Long productId) {
        return ResultUtils.success(reviewSentimentService.syncSentiment(productId));
    }

    @Operation(summary = "全局口碑统计", description = "聚合全部已分析评价的情感分布与好评关键词（仪表盘饼图数据源）")
    @GetMapping("/sentiment/stats")
    public BaseResponse<ReviewSummaryVO> sentimentStats() {
        return ResultUtils.success(reviewSentimentService.getGlobalSentimentStats());
    }
}
