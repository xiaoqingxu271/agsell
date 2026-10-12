package com.lichun.agsell.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lichun.agsell.model.entity.UserProductView;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserProductViewMapper extends BaseMapper<UserProductView> {

    /**
     * 原子落足迹：当日首次浏览则插入（view_count=1），同日再访问只 +1 并刷新最后浏览时间。
     * 依赖 uk_user_product_date 唯一索引，避免先查后改的竞态（并发进详情页不会丢计数）。
     * id 由应用层生成雪花ID传入（表无 AUTO_INCREMENT）。
     */
    @Insert("INSERT INTO user_product_view(id, user_id, product_id, category_id, view_date, view_count, last_view_time) " +
            "VALUES(#{id}, #{userId}, #{productId}, #{categoryId}, CURDATE(), 1, NOW()) " +
            "ON DUPLICATE KEY UPDATE view_count = view_count + 1, last_view_time = NOW()")
    int upsertDailyView(@Param("id") long id,
                        @Param("userId") long userId,
                        @Param("productId") long productId,
                        @Param("categoryId") long categoryId);
}
