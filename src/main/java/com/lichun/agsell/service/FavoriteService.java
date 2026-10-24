package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.vo.FavoriteItemVO;

/**
 * 商品收藏服务
 */
public interface FavoriteService {

    /** 收藏商品（幂等：已收藏直接成功） */
    void add(Long productId);

    /** 取消收藏（幂等：未收藏直接成功）。返回是否删掉了行。 */
    boolean remove(Long productId);

    /** 我的收藏分页（按收藏时间倒序；商品已删除的条目自动剔除） */
    Page<FavoriteItemVO> pageMine(int pageNum, int pageSize);

    /** 当前用户是否已收藏该商品 */
    boolean exists(Long productId);

    /** 我的收藏总数 */
    long countMine();
}
