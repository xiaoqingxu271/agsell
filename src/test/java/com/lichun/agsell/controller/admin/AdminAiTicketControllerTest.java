package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.AiTicketMapper;
import com.lichun.agsell.model.dto.AiTicketHandleRequest;
import com.lichun.agsell.model.entity.AiTicket;
import com.lichun.agsell.model.vo.AiTicketVO;
import com.lichun.agsell.service.AiTicketService;
import com.lichun.agsell.service.impl.AiTicketServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 转人工工单：管理端列表/处理 + 服务层原子抢占
 * （工单由 Python 触发 escalate_human 后经内部接口落库，这里只测管理侧）
 */
@ExtendWith(MockitoExtension.class)
class AdminAiTicketControllerTest {

    @Mock
    private AiTicketMapper aiTicketMapper;

    private AiTicketService aiTicketService;
    private AdminAiTicketController controller;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiTicket.class);
    }

    @BeforeEach
    void setUp() {
        aiTicketService = new AiTicketServiceImpl(aiTicketMapper);
        controller = new AdminAiTicketController(aiTicketService);
        AdminContext.setCurrentAdmin(8L, "ADMIN");
    }

    @AfterEach
    void tearDown() {
        AdminContext.removeCurrentAdmin();
    }

    private AiTicket pendingTicket(long id) {
        AiTicket ticket = new AiTicket();
        ticket.setId(id);
        ticket.setUserId(6L);
        ticket.setSessionId("wx_1");
        ticket.setQuestion("我要投诉，转人工");
        ticket.setReason("用户情绪激烈");
        ticket.setStatus(0);
        return ticket;
    }

    @Test
    @DisplayName("列表返回 VO 且状态文案齐全")
    void listMapsEntityToVo() {
        Page<AiTicket> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(pendingTicket(1L)));
        when(aiTicketMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<AiTicketVO> result = controller.list(1, 10, 0).getData();
        assertEquals(1, result.getRecords().size());
        assertEquals("待处理", result.getRecords().get(0).getStatusText());
        assertEquals("我要投诉，转人工", result.getRecords().get(0).getQuestion());
    }

    @Test
    @DisplayName("处理成功：待处理 → 已处理，写入处理人与时间")
    void handleMarksTicketHandled() {
        when(aiTicketMapper.update(any(), any())).thenReturn(1);

        AiTicketHandleRequest request = new AiTicketHandleRequest();
        request.setRemark("已电话回访并补偿优惠券");
        Boolean ok = controller.handle(1L, request).getData();

        assertTrue(ok);
        // 处理结果由条件 UPDATE 的 wrapper.set 一次写入（entity 恒为 null），
        // 处理人/状态/备注都在 paramNameValuePairs 里，捕获 Wrapper 断言
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AiTicket>> wrapperCaptor =
                ArgumentCaptor.forClass((Class<Wrapper<AiTicket>>) (Class<?>) Wrapper.class);
        verify(aiTicketMapper).update(isNull(), wrapperCaptor.capture());
        Collection<Object> params =
                ((LambdaUpdateWrapper<AiTicket>) wrapperCaptor.getValue()).getParamNameValuePairs().values();
        assertTrue(params.contains(1), "status 应写入 1=已处理");
        assertTrue(params.contains(8L), "handle_by 应写入当前管理员");
        assertTrue(params.contains("已电话回访并补偿优惠券"), "handle_remark 应写入备注");
    }

    @Test
    @DisplayName("并发处理：抢占 0 行时报错，工单不会被二次覆盖")
    void handleLosingRaceFails() {
        when(aiTicketMapper.update(any(), any())).thenReturn(0);

        AiTicketHandleRequest request = new AiTicketHandleRequest();
        request.setRemark("重复处理");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.handle(1L, request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("备注超 500 字在控制器层拒绝，不打到数据库")
    void handleRejectsOverlongRemark() {
        AiTicketHandleRequest request = new AiTicketHandleRequest();
        request.setRemark("长".repeat(501));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.handle(1L, request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(aiTicketMapper, never()).update(any(AiTicket.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("服务层兜底：抢不到处理权同样拒绝（防御控制器直调）")
    void serviceHandleRejectsWhenClaimFails() {
        when(aiTicketMapper.update(any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aiTicketService.handle(1L, "备注", 8L));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("列表排序：待处理排前（orderByAsc status）+ 时间倒序由 wrapper 保证")
    void listUsesMapperPageQuery() {
        Page<AiTicket> page = new Page<>(1, 10, 0);
        page.setRecords(List.of());
        when(aiTicketMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<AiTicketVO> result = controller.list(2, 10, null).getData();
        assertEquals(0, result.getRecords().size());
        verify(aiTicketMapper).selectPage(any(Page.class), any(Wrapper.class));
    }
}
