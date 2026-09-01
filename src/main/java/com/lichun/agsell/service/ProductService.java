package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.ProductCreateRequest;
import com.lichun.agsell.model.dto.ProductListRequest;
import com.lichun.agsell.model.dto.ProductQueryRequest;
import com.lichun.agsell.model.vo.ProductDetailVO;
import com.lichun.agsell.model.vo.ProductListItemVO;

import java.util.List;

public interface ProductService {

    /**
     * 管理端：商品列表（分页+筛选）
     */
    Page<ProductListItemVO> listProducts(ProductQueryRequest request);

    /**
     * 新增或更新商品
     */
    void saveOrUpdateProduct(ProductCreateRequest request);

    /**
     * 删除商品
     */
    void deleteProduct(Long id);

    /**
     * 更新商品状态（上架/下架）
     */
    void updateProductStatus(Long id, Integer status);

    /**
     * 获取商品详情
     */
    ProductDetailVO getProductDetail(Long id);

    /**
     * 用户端：商品列表（分页+筛选+排序）
     */
    Page<ProductListItemVO> listProducts(ProductListRequest request);

    /**
     * 用户端：热销商品
     */
    List<ProductListItemVO> listHotProducts(int limit);

    /**
     * 用户端：新品推荐
     */
    List<ProductListItemVO> listNewProducts(int limit);
}
