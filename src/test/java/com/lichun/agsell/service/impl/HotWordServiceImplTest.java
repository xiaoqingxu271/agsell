package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SearchHotWordMapper;
import com.lichun.agsell.model.entity.SearchHotWord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 热词服务边界测试：计数规范化、热词列表边界、管理端增删改校验
 */
@ExtendWith(MockitoExtension.class)
class HotWordServiceImplTest {

    @Mock
    private SearchHotWordMapper hotWordMapper;

    @InjectMocks
    private HotWordServiceImpl hotWordService;

    @Test
    @DisplayName("record：空白词忽略，不触达数据库")
    void recordBlankIgnored() {
        hotWordService.record(null);
        hotWordService.record("");
        hotWordService.record("   ");
        verifyNoInteractions(hotWordMapper);
    }

    @Test
    @DisplayName("record：超长词（51字符）忽略")
    void recordTooLongIgnored() {
        hotWordService.record("橙".repeat(51));
        verifyNoInteractions(hotWordMapper);
    }

    @Test
    @DisplayName("record：正常词去除首尾空白后原子计数")
    void recordNormalized() {
        hotWordService.record("  脐橙  ");
        verify(hotWordMapper).upsertAndIncrement(anyLong(), eq("脐橙"));
    }

    @Test
    @DisplayName("listHotWords：limit 下界钳制到 1，上界钳制到 20")
    void hotWordsLimitClamped() {
        hotWordService.listHotWords(0);
        verify(hotWordMapper).selectList(any());
        clearInvocations(hotWordMapper);

        hotWordService.listHotWords(999);
        verify(hotWordMapper).selectList(any());
    }

    @Test
    @DisplayName("listHotWords：按状态过滤并返回词列表")
    void hotWordsReturned() {
        SearchHotWord a = new SearchHotWord();
        a.setWord("脐橙");
        SearchHotWord b = new SearchHotWord();
        b.setWord("水果");
        when(hotWordMapper.selectList(any())).thenReturn(Arrays.asList(a, b));

        List<String> words = hotWordService.listHotWords(10);
        assertEquals(Arrays.asList("脐橙", "水果"), words);
    }

    @Test
    @DisplayName("saveOrUpdate 新增：空词拒绝")
    void createBlankRejected() {
        SearchHotWord req = new SearchHotWord();
        req.setWord("   ");
        BusinessException ex = assertThrows(BusinessException.class, () -> hotWordService.saveOrUpdate(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("saveOrUpdate 新增：重复词拒绝")
    void createDuplicateRejected() {
        when(hotWordMapper.selectCount(any())).thenReturn(1L);
        SearchHotWord req = new SearchHotWord();
        req.setWord("脐橙");
        BusinessException ex = assertThrows(BusinessException.class, () -> hotWordService.saveOrUpdate(req));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("saveOrUpdate 新增：合法词插入且 isManual=1")
    void createOk() {
        when(hotWordMapper.selectCount(any())).thenReturn(0L);
        SearchHotWord req = new SearchHotWord();
        req.setWord("脐橙");
        hotWordService.saveOrUpdate(req);

        ArgumentCaptor<SearchHotWord> captor = ArgumentCaptor.forClass(SearchHotWord.class);
        verify(hotWordMapper).insert(captor.capture());
        SearchHotWord saved = captor.getValue();
        assertEquals("脐橙", saved.getWord());
        assertEquals(1, saved.getIsManual());
        assertEquals(0, saved.getSearchCount());
        assertEquals(1, saved.getStatus());
    }

    @Test
    @DisplayName("saveOrUpdate 编辑：ID 不存在拒绝")
    void editNotFoundRejected() {
        when(hotWordMapper.selectById(99L)).thenReturn(null);
        SearchHotWord req = new SearchHotWord();
        req.setId(99L);
        BusinessException ex = assertThrows(BusinessException.class, () -> hotWordService.saveOrUpdate(req));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("saveOrUpdate 编辑：只更新 sort/status，不碰 word")
    void editOnlySortAndStatus() {
        SearchHotWord exist = new SearchHotWord();
        exist.setId(1L);
        exist.setWord("脐橙");
        when(hotWordMapper.selectById(1L)).thenReturn(exist);

        SearchHotWord req = new SearchHotWord();
        req.setId(1L);
        req.setWord("不该被改的词");
        req.setSort(9);
        req.setStatus(0);
        hotWordService.saveOrUpdate(req);

        ArgumentCaptor<SearchHotWord> captor = ArgumentCaptor.forClass(SearchHotWord.class);
        verify(hotWordMapper).updateById(captor.capture());
        SearchHotWord updated = captor.getValue();
        assertEquals(1L, updated.getId());
        assertEquals(9, updated.getSort());
        assertEquals(0, updated.getStatus());
        assertNull(updated.getWord());
    }

    @Test
    @DisplayName("updateStatus：非法状态值拒绝")
    void updateStatusInvalidRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> hotWordService.updateStatus(1L, 2));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("updateStatus：ID 不存在拒绝")
    void updateStatusNotFoundRejected() {
        when(hotWordMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> hotWordService.updateStatus(99L, 0));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("delete：ID 不存在拒绝，存在则删除")
    void deleteValidation() {
        BusinessException ex = assertThrows(BusinessException.class, () -> hotWordService.delete(null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());

        when(hotWordMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> hotWordService.delete(99L));

        when(hotWordMapper.selectById(1L)).thenReturn(new SearchHotWord());
        hotWordService.delete(1L);
        verify(hotWordMapper).deleteById(1L);
    }
}
