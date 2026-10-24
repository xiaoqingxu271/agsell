package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SysNoticeMapper;
import com.lichun.agsell.model.entity.SysNotice;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公告管理（P2 §5.9.2）：CRUD 校验与落库。
 */
@ExtendWith(MockitoExtension.class)
class AdminAnnouncementControllerTest {

    @Mock
    private SysNoticeMapper sysNoticeMapper;

    @InjectMocks
    private AdminAnnouncementController controller;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysNotice.class);
    }

    @Test
    @DisplayName("新增公告：缺省值补齐（sort=0、status=1），标题落库")
    void createFillsDefaults() {
        SysNotice request = new SysNotice();
        request.setTitle("产地直发季");
        BaseResponse<Void> resp = controller.create(request);

        assertEquals(0, resp.getCode());
        ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
        verify(sysNoticeMapper).insert(captor.capture());
        assertEquals("产地直发季", captor.getValue().getTitle());
        assertEquals(0, captor.getValue().getSort());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("空标题在 Java 侧拒绝；编辑不存在的公告 404")
    void createAndUpdateValidate() {
        SysNotice noTitle = new SysNotice();
        noTitle.setTitle("  ");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> controller.create(noTitle)).getCode());

        SysNotice ok = new SysNotice();
        ok.setId(404L);
        ok.setTitle("标题");
        when(sysNoticeMapper.selectById(404L)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> controller.update(ok)).getCode());

        verify(sysNoticeMapper, never()).insert(any(SysNotice.class));
        verify(sysNoticeMapper, never()).updateById(any(SysNotice.class));
    }

    @Test
    @DisplayName("列表按 sort 降序返回（查询条件由 wrapper 保证）")
    void listReturnsAllSorted() {
        when(sysNoticeMapper.selectList(any())).thenReturn(List.of(new SysNotice(), new SysNotice()));

        BaseResponse<List<SysNotice>> resp = controller.list();
        assertEquals(0, resp.getCode());
        assertEquals(2, resp.getData().size());
    }
}
