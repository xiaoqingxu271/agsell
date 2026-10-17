package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.SearchRequest;
import com.lichun.agsell.model.entity.SearchHotWord;
import com.lichun.agsell.model.vo.ProductSearchVO;

import java.util.List;

public interface SearchService {

    /**
     * 用户端商品搜索（全文检索 + 高亮；单字符自动降级 LIKE）
     *
     * @param userId 登录用户 ID（由控制层从 JWT 解析，游客为 null），用于语义召回的个性化重排
     */
    Page<ProductSearchVO> search(SearchRequest request, Long userId);
}
