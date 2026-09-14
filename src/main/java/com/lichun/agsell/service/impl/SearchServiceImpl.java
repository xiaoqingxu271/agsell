package com.lichun.agsell.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.ProductCategoryMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSearchMapper;
import com.lichun.agsell.model.dto.SearchRequest;
import com.lichun.agsell.model.entity.Product;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @Override
    public Page<ProductSearchVO> search(SearchRequest request) {
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

        List<Product> products;
        long total;
        if (keyword.length() < 2) {
            // 单字符：bigram 索引无法可靠命中，降级 LIKE
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Product::getStatus, 1)
                    .and(w -> w.like(Product::getName, keyword)
                            .or().like(Product::getSubtitle, keyword));
            if (request.getCategoryId() != null) {
                wrapper.eq(Product::getCategoryId, request.getCategoryId());
            }
            applySort(wrapper, sortBy);
            Page<Product> page = productMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            products = page.getRecords();
            total = page.getTotal();
        } else {
            long offset = (long) (pageNum - 1) * pageSize;
            products = productSearchMapper.searchByFulltext(keyword, request.getCategoryId(), sortBy, offset, pageSize);
            total = productSearchMapper.countByFulltext(keyword, request.getCategoryId());
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
