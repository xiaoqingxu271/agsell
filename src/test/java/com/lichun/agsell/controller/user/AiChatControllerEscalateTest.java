package com.lichun.agsell.controller.user;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.mapper.AiTicketMapper;
import com.lichun.agsell.model.dto.AiChatRequest;
import com.lichun.agsell.model.entity.AiTicket;
import com.lichun.agsell.utils.UserContextResolver;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户主动转人工（POST /ai/chat/escalate）测试（Mockito，不依赖真实数据库）
 * 覆盖：正常建单（游客/登录）、15 分钟幂等去重、sessionId 校验、长文本截断
 */
@ExtendWith(MockitoExtension.class)
class AiChatControllerEscalateTest {

    @Mock
    private UserContextResolver userContextResolver;

    @Mock
    private AiTicketMapper aiTicketMapper;

    @InjectMocks
    private AiChatController controller;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiTicket.class);
    }

    private AiChatRequest request(String sessionId, String message) {
        AiChatRequest req = new AiChatRequest();
        req.setSessionId(sessionId);
        req.setMessage(message);
        return req;
    }

    @Test
    @DisplayName("游客主动转人工：建单 reason=用户主动转人工、status=0、userId 为空")
    void escalate_guest_createsTicket() {
        when(userContextResolver.resolveUserId(any())).thenReturn(null);
        when(aiTicketMapper.selectOne(any())).thenReturn(null);
        when(aiTicketMapper.insert(any(AiTicket.class))).thenAnswer(inv -> {
            AiTicket t = inv.getArgument(0);
            t.setId(100L);
            return 1;
        });

        var resp = controller.escalate(request("wx_1", "你们的水果坏了怎么赔"), null);

        assertEquals(0, resp.getCode());
        assertEquals(100L, resp.getData());
        ArgumentCaptor<AiTicket> captor = ArgumentCaptor.forClass(AiTicket.class);
        verify(aiTicketMapper).insert(captor.capture());
        AiTicket saved = captor.getValue();
        assertEquals("用户主动转人工", saved.getReason());
        assertEquals(0, saved.getStatus());
        assertNull(saved.getUserId());
        assertEquals("wx_1", saved.getSessionId());
        assertEquals("你们的水果坏了怎么赔", saved.getQuestion());
    }

    @Test
    @DisplayName("15 分钟内已有待处理工单：幂等返回原单号，不重复建单")
    void escalate_idempotentWithinWindow() {
        when(userContextResolver.resolveUserId(any())).thenReturn(7L);
        AiTicket pending = new AiTicket();
        pending.setId(88L);
        pending.setCreateTime(LocalDateTime.now().minusMinutes(5));
        when(aiTicketMapper.selectOne(any())).thenReturn(pending);

        var resp = controller.escalate(request("wx_1", "再转一次"), null);

        assertEquals(88L, resp.getData());
        verify(aiTicketMapper, never()).insert(any(AiTicket.class));
    }

    @Test
    @DisplayName("待处理工单已超过 15 分钟：视为过期，允许再建新单")
    void escalate_oldPendingTicket_ignored() {
        when(userContextResolver.resolveUserId(any())).thenReturn(7L);
        AiTicket old = new AiTicket();
        old.setId(66L);
        old.setCreateTime(LocalDateTime.now().minusMinutes(30));
        when(aiTicketMapper.selectOne(any())).thenReturn(old);
        when(aiTicketMapper.insert(any(AiTicket.class))).thenAnswer(inv -> {
            inv.getArgument(0);
            return 1;
        });

        var resp = controller.escalate(request("wx_1", ""), null);

        assertEquals(0, resp.getCode());
        verify(aiTicketMapper).insert(any(AiTicket.class));
    }

    @Test
    @DisplayName("sessionId 为空：PARAMS_ERROR，不触库")
    void escalate_blankSessionId_rejected() {
        assertThrows(BusinessException.class,
                () -> controller.escalate(request("  ", "x"), null));
        verify(aiTicketMapper, never()).insert(any(AiTicket.class));
    }

    @Test
    @DisplayName("问题描述超 500 字：截断到 500")
    void escalate_longQuestion_truncated() {
        when(userContextResolver.resolveUserId(any())).thenReturn(null);
        when(aiTicketMapper.selectOne(any())).thenReturn(null);
        when(aiTicketMapper.insert(any(AiTicket.class))).thenAnswer(inv -> 1);

        String longText = "问".repeat(800);
        controller.escalate(request("wx_2", longText), null);

        ArgumentCaptor<AiTicket> captor = ArgumentCaptor.forClass(AiTicket.class);
        verify(aiTicketMapper).insert(captor.capture());
        assertEquals(500, captor.getValue().getQuestion().length());
    }
}
