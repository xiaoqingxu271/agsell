package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.SeckillActivityRequest;
import com.lichun.agsell.model.vo.SeckillActivityVO;
import com.lichun.agsell.service.SeckillService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端-秒杀活动管理
 */
@Tag(name = "管理端-秒杀", description = "秒杀活动管理（建档/编辑/上下架/删除）")
@RestController
@RequestMapping("/admin/seckill")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "秒杀活动分页查询")
    @GetMapping("/page")
    public BaseResponse<Page<SeckillActivityVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ResultUtils.success(seckillService.pageActivities(pageNum, pageSize, keyword, status));
    }

    @Operation(summary = "创建秒杀活动")
    @PostMapping
    public BaseResponse<Map<String, Long>> create(@RequestBody SeckillActivityRequest request) {
        Long id = seckillService.createActivity(request);
        return ResultUtils.success(Map.of("id", id));
    }

    @Operation(summary = "编辑秒杀活动")
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable Long id, @RequestBody SeckillActivityRequest request) {
        seckillService.updateActivity(id, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除秒杀活动")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        seckillService.deleteActivity(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "秒杀活动上下架")
    @PutMapping("/{id}/status")
    public BaseResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        seckillService.updateActivityStatus(id, status);
        return ResultUtils.success(null);
    }
}
