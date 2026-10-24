package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.model.dto.AiTicketHandleRequest;
import com.lichun.agsell.model.vo.AiTicketVO;
import com.lichun.agsell.service.AiTicketService;
import com.lichun.agsell.utils.ResultUtils;
import com.lichun.agsell.utils.ThrowUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-AI 转人工工单
 * <p>
 * 路径挂在 /admin/ai 下：JwtAuthInterceptor 的 OPERATOR_FORBIDDEN_PREFIXES
 * 已把 /api/admin/ai 列为运营专员禁入，工单与知识库同口径（改答案来源与跟进客诉同级别）。
 */
@Tag(name = "管理端-AI 工单", description = "AI 客服转人工工单的查看与处理")
@RestController
@RequestMapping("/admin/ai/ticket")
@RequiredArgsConstructor
public class AdminAiTicketController {

    private final AiTicketService aiTicketService;

    @Operation(summary = "工单分页（status 为空查全部，待处理排前）")
    @GetMapping("/list")
    public BaseResponse<Page<AiTicketVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) Integer status) {
        return ResultUtils.success(aiTicketService.page(pageNum, pageSize, status));
    }

    @Operation(summary = "处理工单（待处理 → 已处理）")
    @PostMapping("/{id}/handle")
    public BaseResponse<Boolean> handle(@PathVariable Long id,
                                        @RequestBody AiTicketHandleRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        ThrowUtils.throwIf(request.getRemark() != null && request.getRemark().length() > 500,
                ErrorCode.PARAMS_ERROR, "处理备注不能超过500字");
        Long adminId = AdminContext.getCurrentAdminId();
        ThrowUtils.throwIf(adminId == null, ErrorCode.ADMIN_NOT_LOGIN_ERROR);
        aiTicketService.handle(id, request.getRemark(), adminId);
        return ResultUtils.success(true);
    }
}
