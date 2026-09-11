package com.lichun.agsell.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.ProductionRecordRequest;
import com.lichun.agsell.model.dto.TraceabilityCreateRequest;
import com.lichun.agsell.model.dto.TraceabilityUpdateRequest;
import com.lichun.agsell.model.vo.ProductionRecordVO;
import com.lichun.agsell.model.vo.TraceabilityCreateResultVO;
import com.lichun.agsell.model.vo.TraceabilityDetailVO;
import com.lichun.agsell.model.vo.TraceabilityListItemVO;
import com.lichun.agsell.service.TraceabilityService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端-产地溯源
 */
@Tag(name = "管理端-产地溯源", description = "溯源信息管理（批次、二维码）、生产记录管理")
@RestController
@RequestMapping("/admin/traceability")
@RequiredArgsConstructor
public class AdminTraceabilityController {

    private final TraceabilityService traceabilityService;

    @Operation(summary = "溯源信息分页查询")
    @GetMapping("/page")
    public BaseResponse<Page<TraceabilityListItemVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String batchNo) {
        return ResultUtils.success(
                traceabilityService.pageTraceability(pageNum, pageSize, productName, batchNo));
    }

    @Operation(summary = "溯源信息详情（含生产记录）")
    @GetMapping("/detail/{id}")
    public BaseResponse<TraceabilityDetailVO> detail(@PathVariable Long id) {
        return ResultUtils.success(traceabilityService.getTraceabilityDetail(id));
    }

    @Operation(summary = "新增溯源信息")
    @PostMapping
    public BaseResponse<TraceabilityCreateResultVO> create(@RequestBody TraceabilityCreateRequest request) {
        return ResultUtils.success(traceabilityService.createTraceability(request));
    }

    @Operation(summary = "编辑溯源信息")
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable Long id, @RequestBody TraceabilityUpdateRequest request) {
        traceabilityService.updateTraceability(id, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除溯源信息（级联删除生产记录）")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        traceabilityService.deleteTraceability(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "生成溯源二维码")
    @PostMapping("/{id}/generate-qr")
    public BaseResponse<Map<String, String>> generateQr(@PathVariable Long id) {
        String url = traceabilityService.generateQrCode(id);
        return ResultUtils.success(Map.of("qrCodeUrl", url));
    }

    @Operation(summary = "生产记录列表")
    @GetMapping("/{id}/records")
    public BaseResponse<List<ProductionRecordVO>> records(@PathVariable Long id) {
        return ResultUtils.success(traceabilityService.listRecords(id));
    }

    @Operation(summary = "新增生产记录")
    @PostMapping("/record")
    public BaseResponse<Map<String, Long>> createRecord(@RequestBody ProductionRecordRequest request) {
        Long recordId = traceabilityService.createRecord(request);
        return ResultUtils.success(Map.of("id", recordId));
    }

    @Operation(summary = "编辑生产记录")
    @PutMapping("/record/{id}")
    public BaseResponse<Void> updateRecord(@PathVariable Long id, @RequestBody ProductionRecordRequest request) {
        traceabilityService.updateRecord(id, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除生产记录")
    @DeleteMapping("/record/{id}")
    public BaseResponse<Void> deleteRecord(@PathVariable Long id) {
        traceabilityService.deleteRecord(id);
        return ResultUtils.success(null);
    }
}
