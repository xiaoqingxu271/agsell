package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.vo.ReviewListItemVO;

public interface AdminReviewService {

    /**
     * 评价列表
     */
    Page<ReviewListItemVO> listReviews(int pageNum, int pageSize, Long productId, Integer replied);

    /**
     * 回复评价
     */
    void replyReview(Long id, String replyContent);

    /**
     * 删除评价
     */
    void deleteReview(Long id);
}
