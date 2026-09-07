package com.lichun.agsell.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.ProductCategoryMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.model.dto.ProductCreateRequest;
import com.lichun.agsell.model.dto.ProductListRequest;
import com.lichun.agsell.model.dto.ProductQueryRequest;
import com.lichun.agsell.model.entity.OrderItem;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductCategory;
import com.lichun.agsell.model.entity.ProductSpec;
import com.lichun.agsell.model.vo.ProductDetailVO;
import com.lichun.agsell.model.vo.ProductListItemVO;
import com.lichun.agsell.service.ProductService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;
    private final ProductCategoryMapper categoryMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public Page<ProductListItemVO> listProducts(ProductQueryRequest request) {
        LambdaQueryWrapper<Product> wrapper = buildAdminQueryWrapper(request);
        Page<Product> page = productMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);

        // 管理端也需要填充分类名
        List<Long> categoryIds = page.getRecords().stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<ProductCategory> categories = categoryIds.isEmpty() ? new ArrayList<>()
                : categoryMapper.selectBatchIds(categoryIds);
        Map<Long, String> categoryMap = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));

        return convertPage(page, categoryMap);
    }

    @Override
    @Transactional
    public void saveOrUpdateProduct(ProductCreateRequest request) {
        validateProduct(request);

        if (request.getId() != null) {
            Product exist = productMapper.selectById(request.getId());
            ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "商品不存在");

            Product update = buildProduct(request);
            update.setId(request.getId());
            productMapper.updateById(update);
            saveOrUpdateSpecs(request.getId(), request.getSpecs());
        } else {
            Product product = buildProduct(request);
            productMapper.insert(product);
            saveOrUpdateSpecs(product.getId(), request.getSpecs());
        }
    }

    @Override
    public void deleteProduct(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
        Product exist = productMapper.selectById(id);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "商品不存在");

        // 存在订单关联的商品不允许删除，保护历史订单数据完整性
        long itemCount = orderItemMapper.selectCount(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getProductId, id));
        ThrowUtils.throwIf(itemCount > 0, ErrorCode.OPERATION_ERROR, "该商品存在订单关联，无法删除");

        productSpecMapper.delete(new LambdaQueryWrapper<ProductSpec>()
                .eq(ProductSpec::getProductId, id));
        productMapper.deleteById(id);
    }

    @Override
    public void updateProductStatus(Long id, Integer status) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
        ThrowUtils.throwIf(status == null || (status != 0 && status != 1),
                ErrorCode.PARAMS_ERROR, "状态值不合法");

        Product exist = productMapper.selectById(id);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "商品不存在");

        Product update = new Product();
        update.setId(id);
        update.setStatus(status);
        productMapper.updateById(update);
    }

    @Override
    public ProductDetailVO getProductDetail(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");

        Product product = productMapper.selectById(id);
        ThrowUtils.throwIf(product == null, ErrorCode.NOT_FOUND_ERROR, "商品不存在");
        ThrowUtils.throwIf(product.getStatus() == 0,
                ErrorCode.OPERATION_ERROR, "商品已下架");

        String categoryName = "";
        String parentCategoryName = "";
        if (product.getCategoryId() != null) {
            ProductCategory category = categoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                categoryName = category.getName();
                if (category.getParentId() != null && category.getParentId() != 0) {
                    ProductCategory parentCategory = categoryMapper.selectById(category.getParentId());
                    if (parentCategory != null) {
                        parentCategoryName = parentCategory.getName();
                    }
                }
            }
        }

        List<ProductSpec> specs = productSpecMapper.selectList(
                new LambdaQueryWrapper<ProductSpec>().eq(ProductSpec::getProductId, id));

        return convertToDetailVO(product, categoryName, parentCategoryName, specs);
    }

    @Override
    public Page<ProductListItemVO> listProducts(ProductListRequest request) {
        Long userId = BaseContext.getCurrentId();
        // userId may be null for anonymous users - query is still valid

        LambdaQueryWrapper<Product> wrapper = buildUserQueryWrapper(request);
        Page<Product> page = productMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);

        List<Long> categoryIds = page.getRecords().stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<ProductCategory> categories = categoryIds.isEmpty() ? new ArrayList<>()
                : categoryMapper.selectBatchIds(categoryIds);
        Map<Long, String> categoryMap = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));

        return convertPage(page, categoryMap);
    }

    @Override
    public List<ProductListItemVO> listHotProducts(int limit) {
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getSales)
                .last("LIMIT " + limit));

        List<Long> categoryIds = list.stream().map(Product::getCategoryId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<ProductCategory> categories = categoryIds.isEmpty() ? new ArrayList<>()
                : categoryMapper.selectBatchIds(categoryIds);
        Map<Long, String> categoryMap = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));

        return list.stream()
                .map(p -> convertToListVO(p, categoryMap.get(p.getCategoryId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductListItemVO> listNewProducts(int limit) {
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getCreateTime)
                .last("LIMIT " + limit));

        List<Long> categoryIds = list.stream().map(Product::getCategoryId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<ProductCategory> categories = categoryIds.isEmpty() ? new ArrayList<>()
                : categoryMapper.selectBatchIds(categoryIds);
        Map<Long, String> categoryMap = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));

        return list.stream()
                .map(p -> convertToListVO(p, categoryMap.get(p.getCategoryId())))
                .collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    private LambdaQueryWrapper<Product> buildAdminQueryWrapper(ProductQueryRequest request) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(request.getName())) {
            wrapper.like(Product::getName, request.getName());
        }
        if (request.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, request.getCategoryId());
        }
        if (request.getStatus() != null) {
            wrapper.eq(Product::getStatus, request.getStatus());
        }
        wrapper.orderByDesc(Product::getSort).orderByDesc(Product::getCreateTime);
        return wrapper;
    }

    private LambdaQueryWrapper<Product> buildUserQueryWrapper(ProductListRequest request) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (request.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, request.getCategoryId());
        }
        if (StrUtil.isNotBlank(request.getKeyword())) {
            wrapper.and(w -> w.like(Product::getName, request.getKeyword())
                    .or().like(Product::getSubtitle, request.getKeyword()));
        }
        String sortBy = request.getSortBy();
        if (sortBy == null || sortBy.isEmpty()) {
            sortBy = "";
        }
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
        return wrapper;
    }

    private void validateProduct(ProductCreateRequest request) {
        ThrowUtils.throwIf(StrUtil.isBlank(request.getName()),
                ErrorCode.PARAMS_ERROR, "商品名称不能为空");
        ThrowUtils.throwIf(request.getName().length() > 100,
                ErrorCode.PARAMS_ERROR, "商品名称不能超过100个字符");
        ThrowUtils.throwIf(request.getCategoryId() == null,
                ErrorCode.PARAMS_ERROR, "分类不能为空");
        ProductCategory category = categoryMapper.selectById(request.getCategoryId());
        ThrowUtils.throwIf(category == null, ErrorCode.PARAMS_ERROR, "分类不存在");
        ThrowUtils.throwIf(request.getPrice() == null || request.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0,
                ErrorCode.PARAMS_ERROR, "价格必须大于0");
        ThrowUtils.throwIf(request.getStock() == null || request.getStock() < 0,
                ErrorCode.PARAMS_ERROR, "库存不能为负数");
    }

    private Product buildProduct(ProductCreateRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setSubtitle(request.getSubtitle());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStock(request.getStock());
        product.setMainImage(request.getMainImage());
        product.setImages(request.getImages());
        product.setDescription(request.getDescription());
        product.setOrigin(request.getOrigin());
        product.setHarvestDate(request.getHarvestDate());
        product.setShelfLife(request.getShelfLife());
        product.setStorage(request.getStorage());
        product.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        product.setSort(request.getSort() != null ? request.getSort() : 0);
        return product;
    }

    private void saveOrUpdateSpecs(Long productId, List<ProductCreateRequest.ProductSpecDTO> specs) {
        productSpecMapper.delete(new LambdaQueryWrapper<ProductSpec>()
                .eq(ProductSpec::getProductId, productId));

        if (specs == null || specs.isEmpty()) {
            return;
        }

        List<ProductSpec> specList = new ArrayList<>();
        for (ProductCreateRequest.ProductSpecDTO specDto : specs) {
            ThrowUtils.throwIf(StrUtil.isBlank(specDto.getSpecName()),
                    ErrorCode.PARAMS_ERROR, "规格名称不能为空");
            ThrowUtils.throwIf(specDto.getSpecName().length() > 50,
                    ErrorCode.PARAMS_ERROR, "规格名称不能超过50个字符");
            ThrowUtils.throwIf(specDto.getPrice() == null
                            || specDto.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0,
                    ErrorCode.PARAMS_ERROR, "规格价格必须大于0");
            ThrowUtils.throwIf(specDto.getStock() == null || specDto.getStock() < 0,
                    ErrorCode.PARAMS_ERROR, "规格库存不能为负数");

            ProductSpec spec = new ProductSpec();
            spec.setProductId(productId);
            spec.setSpecName(specDto.getSpecName());
            spec.setPrice(specDto.getPrice());
            spec.setStock(specDto.getStock());
            spec.setImage(specDto.getImage());
            specList.add(spec);
        }
        productSpecMapper.insert(specList);
    }

    private Page<ProductListItemVO> convertPage(Page<Product> page, Map<Long, String> categoryMap) {
        Map<Long, String> finalMap = categoryMap != null ? categoryMap : Map.of();
        Page<ProductListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(p -> convertToListVO(p, finalMap.get(p.getCategoryId())))
                .collect(Collectors.toList()));
        return result;
    }

    private ProductListItemVO convertToListVO(Product product, String categoryName) {
        ProductListItemVO vo = new ProductListItemVO();
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

    private ProductDetailVO convertToDetailVO(Product product, String categoryName,
                                               String parentCategoryName,
                                               List<ProductSpec> specs) {
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setSubtitle(product.getSubtitle());
        vo.setCategoryName(categoryName);
        vo.setParentCategoryName(parentCategoryName);
        vo.setCategoryId(product.getCategoryId());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setStock(product.getStock());
        vo.setSales(product.getSales());
        vo.setMainImage(product.getMainImage());
        vo.setDescription(product.getDescription());
        vo.setOrigin(product.getOrigin());
        vo.setHarvestDate(product.getHarvestDate());
        vo.setShelfLife(product.getShelfLife());
        vo.setStorage(product.getStorage());
        vo.setStatus(product.getStatus());
        vo.setCreateTime(product.getCreateTime());

        if (StrUtil.isNotBlank(product.getImages())) {
            try {
                vo.setImages(JSONUtil.toList(product.getImages(), String.class));
            } catch (Exception e) {
                vo.setImages(new ArrayList<>());
            }
        } else {
            vo.setImages(new ArrayList<>());
        }

        if (specs != null && !specs.isEmpty()) {
            vo.setSpecs(specs.stream().map(spec -> {
                ProductDetailVO.ProductSpecVO specVo = new ProductDetailVO.ProductSpecVO();
                specVo.setId(spec.getId());
                specVo.setSpecName(spec.getSpecName());
                specVo.setPrice(spec.getPrice());
                specVo.setStock(spec.getStock());
                specVo.setImage(spec.getImage());
                return specVo;
            }).collect(Collectors.toList()));
        }

        return vo;
    }
}
