package com.lichun.agsell.controller.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.vo.UserMessageVO;
import com.lichun.agsell.service.UserMessageService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端-消息中心
 * <p>
 * 首页铃铛入口：未读数（红点）、消息列表（发货/售后处理结果）、一键已读。
 * 接口不在 JWT 白名单内，未登录由拦截器统一拒绝。
 */
@Tag(name = "用户端-消息中心", description = "站内消息：订单发货/售后处理结果通知")
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final UserMessageService userMessageService;

    @Operation(summary = "我的消息分页")
    @GetMapping("/list")
    public BaseResponse<Page<UserMessageVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(userMessageService.pageMine(pageNum, pageSize));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    public BaseResponse<Long> unreadCount() {
        return ResultUtils.success(userMessageService.unreadCount());
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public BaseResponse<Integer> readAll() {
        return ResultUtils.success(userMessageService.markAllRead());
    }
}
