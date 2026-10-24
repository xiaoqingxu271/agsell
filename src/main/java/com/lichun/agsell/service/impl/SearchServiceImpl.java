package com.lichun.agsell.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.client.AiSearchClient;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.ProductCategoryMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSearchMapper;
import com.lichun.agsell.mapper.UserSearchLogMapper;
import com.lichun.agsell.model.dto.SearchRequest;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.UserSearchLog;
import com.lichun.agsell.model.entity.ProductCategory;
import com.lichun.agsell.model.vo.ProductListItemVO;
import com.lichun.agsell.model.vo.ProductSearchVO;
import com.lichun.agsell.service.HotWordService;
import com.lichun.agsell.service.SearchService;
import com.lichun.agsell.utils.HighlightUtil;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /** 搜索词长度上限（与 search_hot_word.word VARCHAR(50) 一致） */
    private static final int MAX_KEYWORD_LENGTH = 50;

    private final ProductSearchMapper productSearchMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final HotWordService hotWordService;
    private final AiSearchClient aiSearchClient;
    private final UserSearchLogMapper userSearchLogMapper;

    @Override
    public Page<ProductSearchVO> search(SearchRequest request, Long userId) {
        String keyword = StrUtil.trim(request.getKeyword());
        ThrowUtils.throwIf(StrUtil.isBlank(keyword), ErrorCode.PARAMS_ERROR, "搜索关键词不能为空");
        ThrowUtils.throwIf(keyword.length() > MAX_KEYWORD_LENGTH,
                ErrorCode.PARAMS_ERROR, "搜索关键词不能超过50个字符");

        int pageNum = request.getPageNum() < 1 ? 1 : request.getPageNum();
        int pageSize = request.getPageSize() < 1 ? 12 : Math.min(request.getPageSize(), 50);
        String sortBy = StrUtil.blankToDefault(request.getSortBy(), "");

        // 热词计数（规范化词，失败不影响搜索主流程）
        try {
            hotWordService.record(keyword);
        } catch (Exception e) {
            log.warn("记录热词失败: {}", keyword, e);
        }

        // 用户搜索词留存（AI 偏好信号：keyword → 品类映射）。best-effort：
        // 日志写失败只降级为"丢一条偏好信号"，绝不能让搜索接口报错
        if (userId != null) {
            try {
                UserSearchLog searchLog = new UserSearchLog();
                searchLog.setUserId(userId);
                searchLog.setKeyword(keyword);
                userSearchLogMapper.insert(searchLog);
            } catch (Exception e) {
                log.warn("记录用户搜索词失败: userId={}, keyword={}", userId, keyword, e);
            }
        }

        List<Product> products;
        long total;

        // 语义通道只在"首页 + 默认相关度排序 + 无分类过滤 + 关键词≥2"时接管；翻页去重要用同一判据
        boolean semanticLane = keyword.length() >= 2 && request.getCategoryId() == null && sortBy.isEmpty();

        // ① 首页：优先 AI 向量语义召回重排，命中则置顶
        List<Product> semantic = null;
        if (semanticLane && pageNum == 1) {
            List<Long> semIds = aiSearchClient.semanticProductIds(keyword, pageSize, userId);
            List<Product> semProducts = fetchOnSaleByIdsInOrder(semIds);
            if (!semProducts.isEmpty()) {
                semantic = mergeSemanticBackfill(semProducts, keyword, pageSize);
            }
        }

        if (semantic != null) {
            products = semantic;
            // 语义首页：总数以全文命中口径兜底，翻页自动回退全文检索
            total = Math.max(products.size(), safeCountFulltext(keyword, null));
        } else if (keyword.length() < 2) {
            // 单字符：bigram 索引无法可靠命中，降级 LIKE
            Page<Product> page = selectByLike(keyword, request.getCategoryId(), sortBy, pageNum, pageSize);
            products = page.getRecords();
            total = page.getTotal();
        } else {
            // ② 全文检索。语义首页把 k 条 AI 命中顶到最前面，其后接"全文结果去掉这 k 条"，
            //    因此第 2 页起既要把这 k 条排除（否则同件商品跨页重复），
            //    又要把偏移回退 k（否则拼接处漏掉 k 条商品）。
            List<Long> excludeIds = List.of();
            long head = 0;
            if (semanticLane && pageNum > 1) {
                List<Product> semHead = fetchOnSaleByIdsInOrder(aiSearchClient.semanticProductIds(keyword, pageSize, userId));
                if (!semHead.isEmpty()) {
                    head = semHead.size();
                    excludeIds = semHead.stream().map(Product::getId).collect(Collectors.toList());
                }
            }
            long offset = (long) (pageNum - 1) * pageSize - head;
            try {
                products = productSearchMapper.searchByFulltext(keyword, request.getCategoryId(), sortBy,
                        offset, pageSize, excludeIds);
                total = head + productSearchMapper.countByFulltext(keyword, request.getCategoryId(), excludeIds);
            } catch (Exception e) {
                // 全文索引缺失/SQL 异常时降级 LIKE：搜索主链路绝不因检索层故障对用户报"系统错误"
                log.warn("全文检索不可用，降级 LIKE 兜底：keyword={}, cause={}", keyword, e.toString());
                Page<Product> page = selectByLike(keyword, request.getCategoryId(), sortBy, pageNum, pageSize);
                products = page.getRecords();
                total = page.getTotal();
            }
        }

        Map<Long, String> categoryMap = loadCategoryMap(products);

        List<ProductSearchVO> vos = products.stream()
                .map(p -> {
                    ProductSearchVO vo = convertToListVO(p, categoryMap.get(p.getCategoryId()));
                    vo.setNameHighlight(HighlightUtil.highlight(p.getName(), keyword));
                    vo.setSubtitleHighlight(HighlightUtil.highlight(p.getSubtitle(), keyword));
                    return vo;
                })
                .collect(Collectors.toList());

        Page<ProductSearchVO> result = new Page<>(pageNum, pageSize, total);
        result.setRecords(vos);
        return result;
    }

    // ==================== 私有方法 ====================

    /** AI 语义命中 id → 在售商品实体，严格按语义相关度顺序返回（下架/软删自动跳过） */
    private List<Product> fetchOnSaleByIdsInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = productMapper.selectBatchIds(ids).stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 语义命中置顶后，用全文检索结果补足当页（去重），避免语义命中过少导致首页空档 */
    private List<Product> mergeSemanticBackfill(List<Product> semProducts, String keyword, int pageSize) {
        if (semProducts.size() >= pageSize) {
            return semProducts;
        }
        Set<Long> have = semProducts.stream().map(Product::getId).collect(Collectors.toCollection(HashSet::new));
        List<Product> merged = new ArrayList<>(semProducts);
        try {
            List<Product> fulltext = productSearchMapper.searchByFulltext(keyword, null, "", 0, pageSize, null);
            for (Product p : fulltext) {
                if (merged.size() >= pageSize) {
                    break;
                }
                if (!have.contains(p.getId())) {
                    merged.add(p);
                }
            }
        } catch (Exception e) {
            // 补足只是锦上添花，检索层故障时保住语义命中结果即可
            log.warn("语义首页补足失败，仅返回语义命中：keyword={}, cause={}", keyword, e.toString());
        }
        return merged;
    }

    /** LIKE 兜底查询：单字符关键词与全文检索不可用两条路径共用 */
    private Page<Product> selectByLike(String keyword, Long categoryId, String sortBy, int pageNum, int pageSize) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .and(w -> w.like(Product::getName, keyword)
                        .or().like(Product::getSubtitle, keyword));
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        applySort(wrapper, sortBy);
        return productMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private long safeCountFulltext(String keyword, List<Long> excludeIds) {
        try {
            return productSearchMapper.countByFulltext(keyword, null, excludeIds);
        } catch (Exception e) {
            log.warn("全文总数统计失败: {}", keyword, e);
            return 0L;
        }
    }

    private void applySort(LambdaQueryWrapper<Product> wrapper, String sortBy) {
        switch (sortBy) {
            case "hot":
                wrapper.orderByDesc(Product::getSales);
                break;
            case "new":
                wrapper.orderByDesc(Product::getCreateTime);
                break;
            case "price_asc":
                wrapper.orderByAsc(Product::getPrice);
                break;
            case "price_desc":
                wrapper.orderByDesc(Product::getPrice);
                break;
            default:
                wrapper.orderByDesc(Product::getSort).orderByDesc(Product::getCreateTime);
        }
    }

    private Map<Long, String> loadCategoryMap(List<Product> products) {
        List<Long> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        List<ProductCategory> categories = categoryMapper.selectBatchIds(categoryIds);
        return categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
    }

    private ProductSearchVO convertToListVO(Product product, String categoryName) {
        ProductSearchVO vo = new ProductSearchVO();
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setSubtitle(product.getSubtitle());
        vo.setCategoryId(product.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setStock(product.getStock());
        vo.setSales(product.getSales());
        vo.setMainImage(product.getMainImage());
        vo.setStatus(product.getStatus());
        vo.setCreateTime(product.getCreateTime());
        return vo;
    }
}
