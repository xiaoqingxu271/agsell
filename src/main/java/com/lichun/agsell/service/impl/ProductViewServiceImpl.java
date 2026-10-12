package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.UserProductViewMapper;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.service.ProductViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 浏览足迹落库：粒度「用户 × 商品 × 自然日」，由 uk_user_product_date 唯一索引做原子 upsert。
 * <p>
 * 表行数天然有界（用户数 × 在售商品数 × 天数），刷同一商品不会膨胀；
 * 同日重复浏览只累加 view_count，偏好聚合侧按日去重，避免自强化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductViewServiceImpl implements ProductViewService {

    private final ProductMapper productMapper;
    private final UserProductViewMapper viewMapper;

    @Override
    public void recordView(Long userId, Long productId) {
        if (userId == null || productId == null) {
            return;
        }
        try {
            Product product = productMapper.selectById(productId);
            if (product == null || product.getCategoryId() == null
                    || product.getStatus() == null || product.getStatus() != 1) {
                return;
            }
            viewMapper.upsertDailyView(IdWorker.getId(), userId, productId, product.getCategoryId());
        } catch (Exception e) {
            // 埋点是旁路：任何异常都只记日志，不影响用户看商品
            log.warn("浏览足迹上报落库失败: userId={}, productId={}, cause={}", userId, productId, e.toString());
        }
    }
}
