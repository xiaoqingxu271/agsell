package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ReviewMapper;
import com.lichun.agsell.mapper.SysUserMapper;
import com.lichun.agsell.model.dto.ReviewCreateRequest;
import com.lichun.agsell.model.entity.Order;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Review;
import com.lichun.agsell.model.vo.ReviewMyVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 链路 D 修复验证：评价模型从「整单一条」重构为「按订单明细评价」
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    private Order buildOrder(int status) {
        Order order = new Order();
        order.setId(100L);
        order.setUserId(1L);
        order.setStatus(status);
        return order;
    }

    private OrderItem buildItem(Long id, Long productId) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(100L);
        item.setProductId(productId);
        item.setProductName("赣南脐橙");
        item.setProductImage("https://img.example.com/orange.jpg");
        item.setSpecName("5斤装");
        item.setQuantity(1);
        return item;
    }

    private ReviewCreateRequest buildRequest(Long orderItemId) {
        ReviewCreateRequest request = new ReviewCreateRequest();
        request.setOrderId(100L);
        request.setOrderItemId(orderItemId);
        request.setRating(5);
        request.setContent("很好吃，新鲜多汁");
        request.setIsAnonymous(0);
        return request;
    }

    // ==================== D1：按订单明细评价 ====================

    @Test
    @DisplayName("一单多商品未指定明细时拒绝评价")
    void multiItemOrderWithoutItemIdFails() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(3));
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildItem(1L, 10L), buildItem(2L, 11L)));

        ReviewCreateRequest request = buildRequest(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.createReview(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(reviewMapper, never()).insert(any(Review.class));
    }

    @Test
    @DisplayName("指定明细评价写入对应商品ID")
    void createReviewWithItemIdTargetsCorrectProduct() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(3));
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildItem(1L, 10L), buildItem(2L, 11L)));
        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        reviewService.createReview(buildRequest(2L));

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(captor.capture());
        Review saved = captor.getValue();
        assertEquals(2L, saved.getOrderItemId());
        assertEquals(11L, saved.getProductId());
        assertEquals(100L, saved.getOrderId());
        assertEquals(1L, saved.getUserId());
        assertEquals(5, saved.getRating());
    }

    @Test
    @DisplayName("同一明细重复评价被拒绝")
    void duplicateReviewOnSameItemFails() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(3));
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildItem(1L, 10L)));
        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.createReview(buildRequest(1L)));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        verify(reviewMapper, never()).insert(any(Review.class));
    }

    @Test
    @DisplayName("订单不属于当前用户时拒绝")
    void reviewForeignOrderFails() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.createReview(buildRequest(1L)));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("未完成订单不能评价")
    void reviewUnfinishedOrderFails() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.createReview(buildRequest(1L)));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("指定不属于该订单的明细被拒绝")
    void reviewItemNotInOrderFails() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(3));
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildItem(1L, 10L)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.createReview(buildRequest(999L)));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("单明细订单不传明细ID仍可评价（兼容旧调用）")
    void singleItemOrderWithoutItemIdWorks() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildOrder(3));
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildItem(1L, 10L)));
        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        reviewService.createReview(buildRequest(null));

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getOrderItemId());
        assertEquals(10L, captor.getValue().getProductId());
    }

    @Test
    @DisplayName("评分非法被拒绝")
    void invalidRatingFails() {
        ReviewCreateRequest request = buildRequest(1L);
        request.setRating(6);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.createReview(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    // ==================== D4：我的评价展示商品信息 ====================

    @Test
    @DisplayName("我的评价从订单明细快照填充商品信息")
    void myReviewsFillProductInfo() {
        Review review = new Review();
        review.setId(1L);
        review.setOrderId(100L);
        review.setOrderItemId(1L);
        review.setProductId(10L);
        review.setUserId(1L);
        review.setRating(5);
        review.setContent("不错");

        Page<Review> page = new Page<>(1, 10);
        page.setRecords(List.of(review));
        page.setTotal(1);
        when(reviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(orderItemMapper.selectBatchIds(anyList())).thenReturn(List.of(buildItem(1L, 10L)));

        Page<ReviewMyVO> result = reviewService.listMyReviews(1, 10);
        assertEquals(1, result.getRecords().size());
        ReviewMyVO vo = result.getRecords().get(0);
        assertEquals("赣南脐橙", vo.getProductName());
        assertEquals("https://img.example.com/orange.jpg", vo.getProductImage());
        assertEquals("5斤装", vo.getSpecName());
    }
}
