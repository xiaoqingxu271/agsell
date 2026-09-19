package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.entity.SearchHotWord;
import com.lichun.agsell.service.HotWordService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-搜索热词管理", description = "热词列表、新增/编辑、启停、删除")
@RestController
@RequestMapping("/admin/hot-word")
@RequiredArgsConstructor
public class AdminHotWordController {

    private final HotWordService hotWordService;

    @Operation(summary = "热词列表（分页）")
    @GetMapping("/list")
    public BaseResponse<Page<SearchHotWord>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(hotWordService.list(pageNum, pageSize));
    }

    @Operation(summary = "新增手工热词 / 编辑排序与状态")
    @PostMapping
    public BaseResponse<Void> saveOrUpdate(@RequestBody SearchHotWord request) {
        hotWordService.saveOrUpdate(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "启停热词")
    @PutMapping("/{id}/status")
    public BaseResponse<Void> updateStatus(@PathVariable Long id,
                                           @RequestParam Integer status) {
        hotWordService.updateStatus(id, status);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除热词")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        hotWordService.delete(id);
        return ResultUtils.success(null);
    }
}
