package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.entity.SearchHotWord;

import java.util.List;

public interface HotWordService {

    /**
     * 记录一次搜索（原子计数：不存在则插入，存在则 +1）
     */
    void record(String word);

    /**
     * 用户端：热门搜索词（展示中，按 sort + search_count 排序）
     */
    List<String> listHotWords(int limit);

    /**
     * 管理端：热词分页列表
     */
    Page<SearchHotWord> list(int pageNum, int pageSize);

    /**
     * 管理端：新增手工热词 / 编辑排序与状态（编辑时 word 不可变更）
     */
    void saveOrUpdate(SearchHotWord request);

    /**
     * 管理端：启停热词
     */
    void updateStatus(Long id, Integer status);

    /**
     * 管理端：删除热词（仅手工词，自动词由统计重建）
     */
    void delete(Long id);
}
