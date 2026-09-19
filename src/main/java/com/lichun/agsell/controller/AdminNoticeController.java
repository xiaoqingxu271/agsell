package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.vo.NoticeSummaryVO;
import com.lichun.agsell.service.AdminNoticeService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-通知中心（导航栏铃铛）
 */
@Tag(name = "管理端-通知中心", description = "导航栏铃铛待办汇总")
@RestController
@RequestMapping("/admin/notice")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    @Operation(summary = "待办汇总")
    @GetMapping("/summary")
    public BaseResponse<NoticeSummaryVO> summary() {
        return ResultUtils.success(adminNoticeService.summary());
    }
}
