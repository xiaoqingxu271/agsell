package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.annotation.OperationLog;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.SysNoticeMapper;
import com.lichun.agsell.model.entity.SysNotice;
import com.lichun.agsell.utils.ResultUtils;
import com.lichun.agsell.utils.ThrowUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端-公告管理（P2 §5.9.2）
 * <p>
 * 首页公告条目的增删改查与启停。内容运营性质（与轮播图同级），ADMIN 与 OPERATOR 可用；
 * 前缀不在 OPERATOR 禁入清单内。
 */
@Tag(name = "管理端-公告管理", description = "首页公告条目维护")
@RestController
@RequestMapping("/admin/announcement")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final SysNoticeMapper sysNoticeMapper;

    @Operation(summary = "公告列表（全部，sort 降序）")
    @GetMapping("/list")
    public BaseResponse<List<SysNotice>> list() {
        return ResultUtils.success(sysNoticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
                .orderByDesc(SysNotice::getSort)
                .orderByDesc(SysNotice::getId)));
    }

    @Operation(summary = "新增公告")
    @OperationLog(module = "公告管理", action = "新增公告", content = "新增公告：#{#request.title}")
    @PostMapping
    public BaseResponse<Void> create(@RequestBody SysNotice request) {
        validate(request);
        SysNotice notice = new SysNotice();
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setSort(request.getSort() == null ? 0 : request.getSort());
        notice.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        sysNoticeMapper.insert(notice);
        return ResultUtils.success(null);
    }

    @Operation(summary = "编辑公告（含启停）")
    @OperationLog(module = "公告管理", action = "编辑公告", content = "编辑公告 #{#request.title}")
    @PutMapping
    public BaseResponse<Void> update(@RequestBody SysNotice request) {
        ThrowUtils.throwIf(request.getId() == null, ErrorCode.PARAMS_ERROR, "公告ID不能为空");
        validate(request);
        SysNotice exist = sysNoticeMapper.selectById(request.getId());
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "公告不存在");

        SysNotice update = new SysNotice();
        update.setId(request.getId());
        update.setTitle(request.getTitle());
        update.setContent(request.getContent());
        update.setSort(request.getSort());
        update.setStatus(request.getStatus());
        sysNoticeMapper.updateById(update);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除公告")
    @OperationLog(module = "公告管理", action = "删除公告", content = "删除公告 #{#id}")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "公告ID不能为空");
        SysNotice exist = sysNoticeMapper.selectById(id);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "公告不存在");
        sysNoticeMapper.deleteById(id);
        return ResultUtils.success(null);
    }

    private void validate(SysNotice request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        ThrowUtils.throwIf(request.getTitle() == null || request.getTitle().isBlank(),
                ErrorCode.PARAMS_ERROR, "公告标题不能为空");
        ThrowUtils.throwIf(request.getTitle().length() > 100,
                ErrorCode.PARAMS_ERROR, "公告标题不能超过100字");
        ThrowUtils.throwIf(request.getContent() != null && request.getContent().length() > 500,
                ErrorCode.PARAMS_ERROR, "公告内容不能超过500字");
        ThrowUtils.throwIf(request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1,
                ErrorCode.PARAMS_ERROR, "状态值不合法");
    }
}
