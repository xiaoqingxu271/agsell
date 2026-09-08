package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ReviewMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.Review;
import com.lichun.agsell.model.entity.SysUser;
import com.lichun.agsell.model.vo.ReviewListItemVO;
import com.lichun.agsell.service.AdminReviewService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReviewServiceImpl implements AdminReviewService {

    private final ReviewMapper reviewMapper;
    private final SysUserMapper userMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;

    @Override
    public Page<ReviewListItemVO> listReviews(int pageNum, int pageSize, Long productId, Integer replied) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(Review::getProductId, productId);
        }
        if (replied != null) {
            if (replied == 1) {
                wrapper.isNotNull(Review::getReplyContent);
            } else {
                wrapper.isNull(Review::getReplyContent);
            }
        }
        wrapper.orderByDesc(Review::getCreateTime);

        Page<Review> page = reviewMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 批量查询用户信息
        List<Long> userIds = page.getRecords().stream()
                .map(Review::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> userMap = userIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        // 批量查询订单明细快照（商品名，快照保证历史一致）
        List<Long> orderItemIds = page.getRecords().stream()
                .map(Review::getOrderItemId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, OrderItem> itemMap = orderItemIds.isEmpty() ? new HashMap<>()
                : orderItemMapper.selectBatchIds(orderItemIds).stream()
                        .collect(Collectors.toMap(OrderItem::getId, i -> i, (a, b) -> a));

        // 无明细快照的（历史数据）回退查询商品表
        List<Long> missingProductIds = page.getRecords().stream()
                .filter(r -> r.getOrderItemId() == null || !itemMap.containsKey(r.getOrderItemId()))
                .map(Review::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = missingProductIds.isEmpty() ? new HashMap<>()
                : productMapper.selectBatchIds(missingProductIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Page<ReviewListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(r -> convertToVO(r, userMap.get(r.getUserId()),
                        itemMap.get(r.getOrderItemId()), productMap.get(r.getProductId())))
                .collect(Collectors.toList()));
        return result;
    }

    private ReviewListItemVO convertToVO(Review review, SysUser user, OrderItem orderItem, Product product) {
        ReviewListItemVO vo = new ReviewListItemVO();
        vo.setId(review.getId());
        vo.setOrderId(review.getOrderId());
        vo.setProductId(review.getProductId());
        vo.setUserId(review.getUserId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setIsAnonymous(review.getIsAnonymous());
        vo.setReplied(review.getReplyContent() != null);
        vo.setReplyContent(review.getReplyContent());
        vo.setCreateTime(review.getCreateTime());

        // 商品名优先取订单明细快照，历史数据无快照时回退商品表
        vo.setProductName(orderItem != null
                ? orderItem.getProductName()
                : (product != null ? product.getName() : null));

        if (user != null && review.getIsAnonymous() == 0) {
            vo.setUserName(user.getUsername());
        } else if (review.getIsAnonymous() == 1) {
            vo.setUserName("匿名用户");
        }
        return vo;
    }

    @Override
    @Transactional
    public void replyReview(Long id, String replyContent) {
        Long adminId = AdminContext.getCurrentAdminId();
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(replyContent == null || replyContent.isBlank(),
                ErrorCode.PARAMS_ERROR, "回复内容不能为空");
        ThrowUtils.throwIf(replyContent.length() > 500,
                ErrorCode.PARAMS_ERROR, "回复内容不能超过500字");

        Review review = reviewMapper.selectById(id);
        ThrowUtils.throwIf(review == null, ErrorCode.NOT_FOUND_ERROR, "评价不存在");

        Review update = new Review();
        update.setId(id);
        update.setReplyContent(replyContent);
        update.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(update);
    }

    @Override
    public void deleteReview(Long id) {
        Long adminId = AdminContext.getCurrentAdminId();
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);

        Review review = reviewMapper.selectById(id);
        ThrowUtils.throwIf(review == null, ErrorCode.NOT_FOUND_ERROR, "评价不存在");

        reviewMapper.deleteById(id);
    }
}
