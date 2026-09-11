package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.ProductionRecordRequest;
import com.lichun.agsell.model.dto.TraceabilityCreateRequest;
import com.lichun.agsell.model.dto.TraceabilityUpdateRequest;
import com.lichun.agsell.model.vo.*;

/**
 * 产地溯源服务
 * 管理端：溯源信息 CRUD + 二维码生成 + 生产记录管理
 * 用户端：按批次号查询溯源档案、商品溯源摘要
 */
public interface TraceabilityService {

    // ==================== 管理端：溯源信息 ====================

    /**
     * 溯源信息分页查询（可按商品名/批次号筛选）
     */
    Page<TraceabilityListItemVO> pageTraceability(int pageNum, int pageSize, String productName, String batchNo);

    /**
     * 溯源信息详情（含生产记录）
     */
    TraceabilityDetailVO getTraceabilityDetail(Long id);

    /**
     * 新增溯源信息（服务端生成批次号，含生产记录批量入库）
     */
    TraceabilityCreateResultVO createTraceability(TraceabilityCreateRequest request);

    /**
     * 编辑溯源信息（批次号不可修改）
     */
    void updateTraceability(Long id, TraceabilityUpdateRequest request);

    /**
     * 删除溯源信息（级联逻辑删除该批次生产记录）
     */
    void deleteTraceability(Long id);

    /**
     * 生成溯源二维码（ZXing 生成 PNG → 上传 OSS → 落库）
     *
     * @return 二维码 URL
     */
    String generateQrCode(Long id);

    // ==================== 管理端：生产记录 ====================

    /**
     * 生产记录列表（按记录日期升序）
     */
    java.util.List<ProductionRecordVO> listRecords(Long traceabilityId);

    /**
     * 新增生产记录
     *
     * @return 生产记录ID
     */
    Long createRecord(ProductionRecordRequest request);

    /**
     * 编辑生产记录
     */
    void updateRecord(Long recordId, ProductionRecordRequest request);

    /**
     * 删除生产记录（逻辑删除）
     */
    void deleteRecord(Long recordId);

    // ==================== 用户端 ====================

    /**
     * 按批次号查询溯源档案（无需登录）
     */
    TracePublicVO getPublicTrace(String batchNo);

    /**
     * 商品溯源摘要（详情页溯源入口，无需登录）
     */
    ProductTraceSummaryVO getProductTraceSummary(Long productId);
}
