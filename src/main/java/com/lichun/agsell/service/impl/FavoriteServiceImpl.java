package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.FavoriteMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.model.entity.Favorite;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.vo.FavoriteItemVO;
import com.lichun.agsell.service.FavoriteService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品收藏服务实现
 * <p>
 * 收藏是 AI 个性化偏好的一路信号（ai-service 只读本表聚合类目偏好），
 * Java 侧只管 CRUD，不做任何通知/索引联动。
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    @Override
    public void add(Long productId) {
        Long userId = requireUserId();
        ThrowUtils.throwIf(productId == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
        Product product = productMapper.selectById(productId);
        ThrowUtils.throwIf(product == null || Objects.equals(product.getDeleted(), 1),
                ErrorCode.NOT_FOUND_ERROR, "商品不存在");

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // (user_id, product_id) 唯一键兜底：并发双击收藏按幂等成功处理
        }
    }

    @Override
    public boolean remove(Long productId) {
        Long userId = requireUserId();
        ThrowUtils.throwIf(productId == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
        return favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)) > 0;
    }

    @Override
    public Page<FavoriteItemVO> pageMine(int pageNum, int pageSize) {
        Long userId = requireUserId();
        Page<Favorite> page = favoriteMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime));

        // 批量取商品快照，避免 N+1；已删除商品从列表剔除（收藏行保留，商品回归后自动恢复）
        List<Long> productIds = page.getRecords().stream().map(Favorite::getProductId).toList();
        Map<Long, Product> productMap = productIds.isEmpty() ? Map.of()
                : productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .in(Product::getId, productIds)
                        .eq(Product::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

        Page<FavoriteItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .filter(f -> productMap.containsKey(f.getProductId()))
                .map(f -> toVO(f, productMap.get(f.getProductId())))
                .toList());
        return result;
    }

    @Override
    public boolean exists(Long productId) {
        Long userId = requireUserId();
        ThrowUtils.throwIf(productId == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)) > 0;
    }

    @Override
    public long countMine() {
        Long userId = requireUserId();
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId));
    }

    private Long requireUserId() {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        return userId;
    }

    private FavoriteItemVO toVO(Favorite favorite, Product product) {
        FavoriteItemVO vo = new FavoriteItemVO();
        vo.setId(favorite.getId());
        vo.setProductId(product.getId());
        vo.setName(product.getName());
        vo.setMainImage(product.getMainImage());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setSales(product.getSales());
        vo.setProductStatus(product.getStatus());
        vo.setFavoriteTime(favorite.getCreateTime());
        return vo;
    }
}
