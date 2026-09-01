package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.ReviewCreateRequest;
import com.lichun.agsell.model.vo.ReviewMyVO;
import com.lichun.agsell.model.vo.ReviewVO;

import java.util.List;

public interface ReviewService {

    /**
     * 提交评价
     */
    void createReview(ReviewCreateRequest request);

    /**
     * 商品评价列表
     */
    Page<ReviewVO> listProductReviews(Long productId, int pageNum, int pageSize);

    /**
     * 我的评价列表
     */
    Page<ReviewMyVO> listMyReviews(int pageNum, int pageSize);
}
