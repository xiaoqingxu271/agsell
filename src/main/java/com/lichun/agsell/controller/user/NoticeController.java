package com.lichun.agsell.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.entity.SysNotice;
import com.lichun.agsell.mapper.SysNoticeMapper;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端-首页公告
 * <p>
 * 首页公告条数据源。接口在 JWT 白名单内（游客可见），只返回展示中的条目。
 */
@Tag(name = "用户端-公告", description = "首页公告条（游客可见）")
@RestController
@RequestMapping("/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final SysNoticeMapper sysNoticeMapper;

    @Operation(summary = "展示中的公告列表（sort 降序）")
    @GetMapping("/list")
    public BaseResponse<List<SysNotice>> list() {
        return ResultUtils.success(sysNoticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, 1)
                .orderByDesc(SysNotice::getSort)
                .orderByDesc(SysNotice::getId)));
    }
}
