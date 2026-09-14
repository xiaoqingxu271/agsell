package com.lichun.agsell.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SearchHotWordMapper;
import com.lichun.agsell.model.entity.SearchHotWord;
import com.lichun.agsell.service.HotWordService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotWordServiceImpl implements HotWordService {

    /** 与表字段 word VARCHAR(50) 保持一致 */
    private static final int MAX_WORD_LENGTH = 50;

    private final SearchHotWordMapper hotWordMapper;

    @Override
    public void record(String word) {
        String normalized = StrUtil.trim(word);
        if (StrUtil.isBlank(normalized) || normalized.length() > MAX_WORD_LENGTH) {
            return;
        }
        // 雪花ID由应用层生成（与 MyBatis-Plus ASSIGN_ID 同源，保证与其他表 id 策略一致）
        hotWordMapper.upsertAndIncrement(IdWorker.getId(), normalized);
    }

    @Override
    public List<String> listHotWords(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        List<SearchHotWord> words = hotWordMapper.selectList(new LambdaQueryWrapper<SearchHotWord>()
                .eq(SearchHotWord::getStatus, 1)
                .orderByDesc(SearchHotWord::getSort)
                .orderByDesc(SearchHotWord::getSearchCount)
                .last("LIMIT " + safeLimit));
        return words.stream()
                .map(SearchHotWord::getWord)
                .collect(Collectors.toList());
    }

    @Override
    public Page<SearchHotWord> list(int pageNum, int pageSize) {
        int safePageNum = pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize < 1 ? 10 : Math.min(pageSize, 50);
        LambdaQueryWrapper<SearchHotWord> wrapper = new LambdaQueryWrapper<SearchHotWord>()
                .orderByDesc(SearchHotWord::getSort)
                .orderByDesc(SearchHotWord::getSearchCount);
        return hotWordMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
    }

    @Override
    public void saveOrUpdate(SearchHotWord request) {
        if (request.getId() == null) {
            // 新增手工词
            String word = StrUtil.trim(request.getWord());
            ThrowUtils.throwIf(StrUtil.isBlank(word), ErrorCode.PARAMS_ERROR, "热词不能为空");
            ThrowUtils.throwIf(word.length() > MAX_WORD_LENGTH,
                    ErrorCode.PARAMS_ERROR, "热词不能超过50个字符");
            Long exists = hotWordMapper.selectCount(
                    new LambdaQueryWrapper<SearchHotWord>().eq(SearchHotWord::getWord, word));
            ThrowUtils.throwIf(exists > 0, ErrorCode.OPERATION_ERROR, "该热词已存在");

            SearchHotWord entity = new SearchHotWord();
            entity.setWord(word);
            entity.setSearchCount(0);
            entity.setSort(request.getSort() != null ? request.getSort() : 0);
            entity.setStatus(request.getStatus() != null ? request.getStatus() : 1);
            entity.setIsManual(1);
            hotWordMapper.insert(entity);
            return;
        }

        // 编辑：仅允许调整排序与状态，word 不可变更（uk_word 约束下变更等于换词）
        SearchHotWord exist = hotWordMapper.selectById(request.getId());
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "热词不存在");
        SearchHotWord update = new SearchHotWord();
        update.setId(request.getId());
        if (request.getSort() != null) {
            update.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            ThrowUtils.throwIf(request.getStatus() != 0 && request.getStatus() != 1,
                    ErrorCode.PARAMS_ERROR, "状态值不合法");
            update.setStatus(request.getStatus());
        }
        hotWordMapper.updateById(update);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "热词ID不能为空");
        ThrowUtils.throwIf(status == null || (status != 0 && status != 1),
                ErrorCode.PARAMS_ERROR, "状态值不合法");
        SearchHotWord exist = hotWordMapper.selectById(id);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "热词不存在");
        SearchHotWord update = new SearchHotWord();
        update.setId(id);
        update.setStatus(status);
        hotWordMapper.updateById(update);
    }

    @Override
    public void delete(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "热词ID不能为空");
        SearchHotWord exist = hotWordMapper.selectById(id);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "热词不存在");
        // 自动统计词删除后仍会随搜索重建，允许删除；手工词直接删除
        hotWordMapper.deleteById(id);
    }
}
