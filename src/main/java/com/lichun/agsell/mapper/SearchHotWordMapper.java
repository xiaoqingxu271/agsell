package com.lichun.agsell.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lichun.agsell.model.entity.SearchHotWord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchHotWordMapper extends BaseMapper<SearchHotWord> {

    /**
     * 原子计数：词不存在则插入（search_count=1），存在则 +1。
     * 依赖 uk_word 唯一索引，避免先查后改的竞态。
     * id 由应用层生成雪花ID 传入（表已无 AUTO_INCREMENT）。
     */
    @Insert("INSERT INTO search_hot_word(id, word, search_count, sort, status, is_manual) " +
            "VALUES(#{id}, #{word}, 1, 0, 1, 0) " +
            "ON DUPLICATE KEY UPDATE search_count = search_count + 1")
    int upsertAndIncrement(@Param("id") long id, @Param("word") String word);
}
