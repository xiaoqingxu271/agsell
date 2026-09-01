package com.lichun.agsell.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ReviewMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.ReviewCreateRequest;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Review;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.vo.ReviewMyVO;
import com.lichun.agsell.model.vo.ReviewVO;
import com.lichun.agsell.service.ReviewService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SysUserMapper userMapper;

    @Override
    public void createReview(ReviewCreateRequest request) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request.getOrderId() == null, ErrorCode.PARAMS_ERROR, "订单ID不能为空");
        ThrowUtils.throwIf(request.getRating() == null || request.getRating() < 1 || request.getRating() > 5,
                ErrorCode.PARAMS_ERROR, "评分必须在1-5之间");
        ThrowUtils.throwIf(request.getContent() != null && request.getContent().length() > 500,
                ErrorCode.PARAMS_ERROR, "评价内容不能超过500字");

        // 校验订单存在且属于当前用户
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, request.getOrderId())
                .eq(Order::getUserId, userId));
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");

        // 校验订单状态为已完成
        ThrowUtils.throwIf(order.getStatus() != 3,
                ErrorCode.OPERATION_ERROR, "只有已完成的订单才能评价");

        // 校验是否已评价
        long existCount = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, request.getOrderId()));
        ThrowUtils.throwIf(existCount > 0,
                ErrorCode.OPERATION_ERROR, "该订单已评价");

        // 构建评价
        Review review = new Review();
        review.setOrderId(request.getOrderId());
        review.setUserId(userId);
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        review.setIsAnonymous(request.getIsAnonymous() != null ? request.getIsAnonymous() : 0);
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            review.setImages(JSONUtil.toJsonStr(request.getImages()));
        }

        // 从订单明细中获取商品ID（取第一条）
        List<OrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, request.getOrderId()));
        ThrowUtils.throwIf(orderItems.isEmpty(), ErrorCode.NOT_FOUND_ERROR, "订单明细不存在");
        review.setProductId(orderItems.get(0).getProductId());

        reviewMapper.insert(review);
    }

    @Override
    public Page<ReviewVO> listProductReviews(Long productId, int pageNum, int pageSize) {
        Page<Review> page = reviewMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProductId, productId)
                        .orderByDesc(Review::getCreateTime));

        // 批量查询用户信息
        List<Long> userIds = page.getRecords().stream()
                .map(Review::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        Page<ReviewVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(r -> convertToVO(r, userMap.get(r.getUserId())))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public Page<ReviewMyVO> listMyReviews(int pageNum, int pageSize) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Page<Review> page = reviewMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreateTime));

        // 批量查询商品信息
        List<Long> productIds = page.getRecords().stream()
                .map(Review::getProductId)
                .distinct()
                .collect(Collectors.toList());

        Page<ReviewMyVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(r -> convertToMyVO(r))
                .collect(Collectors.toList()));
        return result;
    }

    // ==================== 私有方法 ====================

    private ReviewVO convertToVO(Review review, SysUser user) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setOrderId(review.getOrderId());
        vo.setProductId(review.getProductId());
        vo.setUserId(review.getUserId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setIsAnonymous(review.getIsAnonymous());
        vo.setReplyContent(review.getReplyContent());
        vo.setReplyTime(review.getReplyTime());
        vo.setCreateTime(review.getCreateTime());

        // 解析图片
        if (review.getImages() != null && !review.getImages().isEmpty()) {
            try {
                vo.setImages(JSONUtil.toList(review.getImages(), String.class));
            } catch (Exception e) {
                vo.setImages(List.of());
            }
        } else {
            vo.setImages(List.of());
        }

        // 匿名时隐藏用户名
        if (user != null && review.getIsAnonymous() == 0) {
            vo.setUserName(user.getNickname());
            vo.setUserAvatar(user.getAvatar());
        } else {
            vo.setUserName("匿名用户");
            vo.setUserAvatar(null);
        }
        return vo;
    }

    private ReviewMyVO convertToMyVO(Review review) {
        ReviewMyVO vo = new ReviewMyVO();
        vo.setId(review.getId());
        vo.setOrderId(review.getOrderId());
        vo.setProductId(review.getProductId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setReplyContent(review.getReplyContent());
        vo.setReplyTime(review.getReplyTime());
        vo.setCreateTime(review.getCreateTime());

        // 解析图片
        if (review.getImages() != null && !review.getImages().isEmpty()) {
            try {
                vo.setImages(JSONUtil.toList(review.getImages(), String.class));
            } catch (Exception e) {
                vo.setImages(List.of());
            }
        } else {
            vo.setImages(List.of());
        }

        // 简化处理：商品名称和图片从订单明细中获取（实际项目中可关联查询）
        return vo;
    }
}
