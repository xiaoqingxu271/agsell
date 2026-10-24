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
     * 批量上架/下架（管理列表多选操作）。返回实际更新条数；
     * 整批一次 UPDATE，索引通知整批一次（方向由 Python 按在售与否判断）。
     */
    int batchUpdateStatus(List<Long> ids, Integer status);

    /**
     * 列表内联快速编辑价格/库存（只改传了的字段，不必打开完整编辑弹窗）。
     * 价格/库存不在召回索引条目文本里，无需通知 AI 侧重建。
     */
    void quickEditPriceStock(Long id, java.math.BigDecimal price, Integer stock);

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

    /**
     * 用户端：按给定 id 顺序返回在售商品（AI 个性化 feed 用，排序权在 Python）
     */
    List<ProductListItemVO> listProductsByIds(List<Long> ids);
}
