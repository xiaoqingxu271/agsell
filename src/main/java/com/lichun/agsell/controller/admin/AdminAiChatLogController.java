package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.mapper.AiChatLogMapper;
import com.lichun.agsell.model.entity.AiChatLog;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-AI 对话审计（只读）
 *
 * 数据源 ai_chat_log 由 Python 每轮旁路落库，这里不做任何写接口——审计数据
 * 不允许被后台篡改。用途：合规抽查、客诉追溯（按会话号/用户拉全轮次）、
 * 结合 refused 筛选复盘"AI 哪些问题答不上"。
 * 路径挂在 /admin/ai 下：与工单/知识库同口径（运营专员禁入）。
 */
@Tag(name = "管理端-AI 对话审计", description = "AI 客服逐轮对话记录（只读）")
@RestController
@RequestMapping("/admin/ai/chatlog")
@RequiredArgsConstructor
public class AdminAiChatLogController {

    private final AiChatLogMapper aiChatLogMapper;

    @Operation(summary = "对话审计分页（可按会话号/用户/拒答轮筛选，最新在前）")
    @GetMapping("/list")
    public BaseResponse<Page<AiChatLog>> list(@RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String sessionId,
                                              @RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) Integer refused) {
        LambdaQueryWrapper<AiChatLog> qw = new LambdaQueryWrapper<>();
        if (sessionId != null && !sessionId.isBlank()) {
            qw.eq(AiChatLog::getSessionId, sessionId.trim());
        }
        qw.eq(userId != null, AiChatLog::getUserId, userId);
        qw.eq(refused != null, AiChatLog::getRefused, refused);
        qw.orderByDesc(AiChatLog::getId);
        return ResultUtils.success(aiChatLogMapper.selectPage(new Page<>(pageNum, pageSize), qw));
    }
}
