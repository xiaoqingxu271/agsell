package com.lichun.agsell.mapper;

import com.lichun.agsell.model.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品全文检索 Mapper（MySQL FULLTEXT / ngram）
 * 自定义 SQL 见 resources/mapper/ProductSearchMapper.xml
 */
@Mapper
public interface ProductSearchMapper {

    /**
     * 全文检索（自然语言模式），按 sortBy 排序
     *
     * @param keyword    搜索关键词（长度 >= 2）
     * @param categoryId 分类过滤，可为 null
     * @param sortBy     排序：空/hot/new/price_asc/price_desc
     * @param offset     分页偏移
     * @param pageSize   每页数量
     */
    List<Product> searchByFulltext(@Param("keyword") String keyword,
                                   @Param("categoryId") Long categoryId,
                                   @Param("sortBy") String sortBy,
                                   @Param("offset") long offset,
                                   @Param("pageSize") int pageSize);

    /**
     * 全文检索命中总数（分页用，口径与 searchByFulltext 完全一致）
     */
    long countByFulltext(@Param("keyword") String keyword,
                         @Param("categoryId") Long categoryId);
}
