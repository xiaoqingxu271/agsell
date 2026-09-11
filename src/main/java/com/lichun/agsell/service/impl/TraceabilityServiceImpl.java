package com.lichun.agsell.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductionRecordMapper;
import com.lichun.agsell.mapper.TraceabilityInfoMapper;
import com.lichun.agsell.model.dto.ProductionRecordRequest;
import com.lichun.agsell.model.dto.TraceabilityCreateRequest;
import com.lichun.agsell.model.dto.TraceabilityUpdateRequest;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductionRecord;
import com.lichun.agsell.model.entity.TraceabilityInfo;
import com.lichun.agsell.model.vo.*;
import com.lichun.agsell.service.FileUploadService;
import com.lichun.agsell.service.TraceabilityService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 产地溯源服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceabilityServiceImpl implements TraceabilityService {

    /** 认证类型 → 中文 */
    private static final Map<String, String> CERTIFICATION_TYPE_TEXT_MAP = Map.of(
            "ORGANIC", "有机认证",
            "GREEN", "绿色食品",
            "GEOGRAPHICAL", "地理标志",
            "NONE", "无");

    /** 生产记录类型 → 中文 */
    private static final Map<String, String> RECORD_TYPE_TEXT_MAP = Map.of(
            "SEEDING", "播种",
            "FERTILIZING", "施肥",
            "WATERING", "浇水",
            "PEST_CONTROL", "病虫害防治",
            "HARVEST", "采收",
            "OTHER", "其他");

    /** 二维码尺寸（像素） */
    private static final int QR_CODE_SIZE = 300;

    /** 批次号生成重试次数（唯一索引冲突兜底） */
    private static final int BATCH_NO_RETRY_TIMES = 3;

    private final TraceabilityInfoMapper traceabilityInfoMapper;
    private final ProductionRecordMapper productionRecordMapper;
    private final ProductMapper productMapper;
    private final FileUploadService fileUploadService;

    /** 溯源 H5 页面域名（二维码内容前缀） */
    @Value("${trace.base-url}")
    private String traceBaseUrl;

    /**
     * 认证类型 → 中文（null 安全，Map.of 不可变 Map 不支持 null key）
     */
    private String certificationTypeText(String certificationType) {
        return certificationType == null ? null
                : CERTIFICATION_TYPE_TEXT_MAP.getOrDefault(certificationType, certificationType);
    }

    /**
     * 生产记录类型 → 中文（null 安全）
     */
    private String recordTypeText(String recordType) {
        return recordType == null ? null
                : RECORD_TYPE_TEXT_MAP.getOrDefault(recordType, recordType);
    }

    // ==================== 管理端：溯源信息 ====================

    @Override
    public Page<TraceabilityListItemVO> pageTraceability(int pageNum, int pageSize, String productName, String batchNo) {
        // 1. 分页查询溯源信息
        LambdaQueryWrapper<TraceabilityInfo> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(batchNo)) {
            wrapper.like(TraceabilityInfo::getBatchNo, batchNo);
        }
        wrapper.orderByDesc(TraceabilityInfo::getCreateTime);

        // 商品名筛选：先按名称查商品ID集合，再 IN 过滤溯源信息
        if (StrUtil.isNotBlank(productName)) {
            List<Long> productIds = productMapper.selectList(
                            new LambdaQueryWrapper<Product>().like(Product::getName, productName))
                    .stream().map(Product::getId).collect(Collectors.toList());
            if (productIds.isEmpty()) {
                Page<TraceabilityListItemVO> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(new ArrayList<>());
                return empty;
            }
            wrapper.in(TraceabilityInfo::getProductId, productIds);
        }

        Page<TraceabilityInfo> page = traceabilityInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 2. 批量联查商品信息，避免 N+1
        List<Long> productIds = page.getRecords().stream()
                .map(TraceabilityInfo::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        // 3. 组装 VO
        Page<TraceabilityListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(info -> {
            TraceabilityListItemVO vo = new TraceabilityListItemVO();
            vo.setId(info.getId());
            vo.setProductId(info.getProductId());
            Product product = productMap.get(info.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setProductImage(product.getMainImage());
            }
            vo.setBatchNo(info.getBatchNo());
            vo.setFarmerName(info.getFarmerName());
            vo.setOrigin(joinOrigin(info));
            vo.setHarvestDate(info.getHarvestDate());
            vo.setCertificationType(info.getCertificationType());
            vo.setCertificationTypeText(certificationTypeText(info.getCertificationType()));
            vo.setQrCodeUrl(info.getQrCodeUrl());
            vo.setCreateTime(info.getCreateTime());
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public TraceabilityDetailVO getTraceabilityDetail(Long id) {
        TraceabilityInfo info = getInfoOrThrow(id);
        Product product = productMapper.selectById(info.getProductId());

        TraceabilityDetailVO vo = new TraceabilityDetailVO();
        vo.setId(info.getId());
        vo.setProductId(info.getProductId());
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setProductImage(product.getMainImage());
        }
        vo.setBatchNo(info.getBatchNo());
        vo.setFarmerName(info.getFarmerName());
        vo.setFarmerPhone(info.getFarmerPhone());
        vo.setOriginProvince(info.getOriginProvince());
        vo.setOriginCity(info.getOriginCity());
        vo.setOriginDistrict(info.getOriginDistrict());
        vo.setPlantingDate(info.getPlantingDate());
        vo.setHarvestDate(info.getHarvestDate());
        vo.setQualityCheckResult(info.getQualityCheckResult());
        vo.setPesticideTest(info.getPesticideTest());
        vo.setCertificationType(info.getCertificationType());
        vo.setCertificationTypeText(certificationTypeText(info.getCertificationType()));
        vo.setCertificationUrls(parseJsonList(info.getCertificationUrls()));
        vo.setQrCodeUrl(info.getQrCodeUrl());
        vo.setCreateTime(info.getCreateTime());
        vo.setProductionRecords(listRecords(info.getId()));
        return vo;
    }

    @Override
    @Transactional
    public TraceabilityCreateResultVO createTraceability(TraceabilityCreateRequest request) {
        validateCreateRequest(request);

        // 批次号生成 + 插入（唯一索引冲突时重试，避免并发同秒生成同序号）
        for (int attempt = 0; attempt < BATCH_NO_RETRY_TIMES; attempt++) {
            String batchNo = nextBatchNo();
            try {
                TraceabilityInfo info = buildInfo(request);
                info.setBatchNo(batchNo);
                traceabilityInfoMapper.insert(info);
                insertRecords(info.getId(), info.getProductId(), batchNo, request.getProductionRecords());
                log.info("[Traceability] 新增溯源信息成功, id={}, batchNo={}", info.getId(), batchNo);
                return new TraceabilityCreateResultVO(info.getId(), batchNo);
            } catch (DuplicateKeyException e) {
                log.warn("[Traceability] 批次号唯一索引冲突，重试 attempt={}", attempt);
                if (attempt == BATCH_NO_RETRY_TIMES - 1) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "批次号生成失败，请重试");
                }
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "批次号生成失败，请重试");
    }

    @Override
    public void updateTraceability(Long id, TraceabilityUpdateRequest request) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "溯源信息ID不能为空");
        TraceabilityInfo exist = getInfoOrThrow(id);
        validateUpdateRequest(request);

        // 显式 set 所有字段（含 null），支持清空可选字段；批次号不可修改
        traceabilityInfoMapper.update(null, new LambdaUpdateWrapper<TraceabilityInfo>()
                .eq(TraceabilityInfo::getId, id)
                .set(TraceabilityInfo::getProductId, request.getProductId())
                .set(TraceabilityInfo::getFarmerName, request.getFarmerName())
                .set(TraceabilityInfo::getFarmerPhone, request.getFarmerPhone())
                .set(TraceabilityInfo::getOriginProvince, request.getOriginProvince())
                .set(TraceabilityInfo::getOriginCity, request.getOriginCity())
                .set(TraceabilityInfo::getOriginDistrict, request.getOriginDistrict())
                .set(TraceabilityInfo::getPlantingDate, request.getPlantingDate())
                .set(TraceabilityInfo::getHarvestDate, request.getHarvestDate())
                .set(TraceabilityInfo::getQualityCheckResult, request.getQualityCheckResult())
                .set(TraceabilityInfo::getPesticideTest, request.getPesticideTest())
                .set(TraceabilityInfo::getCertificationType, request.getCertificationType())
                .set(TraceabilityInfo::getCertificationUrls,
                        request.getCertificationUrls() == null ? null : JSONUtil.toJsonStr(request.getCertificationUrls())));
        log.info("[Traceability] 编辑溯源信息成功, id={}, batchNo={}", id, exist.getBatchNo());
    }

    @Override
    @Transactional
    public void deleteTraceability(Long id) {
        TraceabilityInfo info = getInfoOrThrow(id);
        // 级联逻辑删除该批次生产记录
        productionRecordMapper.delete(new LambdaQueryWrapper<ProductionRecord>()
                .eq(ProductionRecord::getBatchNo, info.getBatchNo()));
        traceabilityInfoMapper.deleteById(id);
        log.info("[Traceability] 删除溯源信息成功, id={}, batchNo={}", id, info.getBatchNo());
    }

    @Override
    public String generateQrCode(Long id) {
        TraceabilityInfo info = getInfoOrThrow(id);
        String content = traceBaseUrl + "/trace?batchNo=" + info.getBatchNo();
        try {
            BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);

            // 固定 objectKey，重复生成覆盖旧图（幂等）
            String objectKey = "trace/qr/" + info.getBatchNo() + ".png";
            String url = fileUploadService.uploadBytes(baos.toByteArray(), objectKey, "image/png");

            TraceabilityInfo update = new TraceabilityInfo();
            update.setId(info.getId());
            update.setQrCodeUrl(url);
            traceabilityInfoMapper.updateById(update);
            log.info("[Traceability] 二维码生成成功, id={}, batchNo={}", id, info.getBatchNo());
            return url;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Traceability] 二维码生成失败, id={}", id, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "二维码生成失败：" + e.getMessage());
        }
    }

    // ==================== 管理端：生产记录 ====================

    @Override
    public List<ProductionRecordVO> listRecords(Long traceabilityId) {
        TraceabilityInfo info = getInfoOrThrow(traceabilityId);
        return productionRecordMapper.selectList(new LambdaQueryWrapper<ProductionRecord>()
                        .eq(ProductionRecord::getBatchNo, info.getBatchNo())
                        .orderByAsc(ProductionRecord::getRecordDate)
                        .orderByAsc(ProductionRecord::getId))
                .stream().map(this::convertToRecordVO).collect(Collectors.toList());
    }

    @Override
    public Long createRecord(ProductionRecordRequest request) {
        ThrowUtils.throwIf(request.getTraceabilityId() == null, ErrorCode.PARAMS_ERROR, "溯源信息ID不能为空");
        TraceabilityInfo info = getInfoOrThrow(request.getTraceabilityId());
        validateRecordRequest(request);

        ProductionRecord record = buildRecord(info.getProductId(), info.getBatchNo(), request);
        productionRecordMapper.insert(record);
        log.info("[Traceability] 新增生产记录成功, id={}, batchNo={}", record.getId(), info.getBatchNo());
        return record.getId();
    }

    @Override
    public void updateRecord(Long recordId, ProductionRecordRequest request) {
        ThrowUtils.throwIf(recordId == null, ErrorCode.PARAMS_ERROR, "生产记录ID不能为空");
        ProductionRecord exist = productionRecordMapper.selectById(recordId);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "生产记录不存在");
        validateRecordRequest(request);

        productionRecordMapper.update(null, new LambdaUpdateWrapper<ProductionRecord>()
                .eq(ProductionRecord::getId, recordId)
                .set(ProductionRecord::getRecordType, request.getRecordType())
                .set(ProductionRecord::getRecordDate, request.getRecordDate())
                .set(ProductionRecord::getContent, request.getContent())
                .set(ProductionRecord::getImages,
                        request.getImages() == null ? null : JSONUtil.toJsonStr(request.getImages()))
                .set(ProductionRecord::getOperator, request.getOperator()));
        log.info("[Traceability] 编辑生产记录成功, id={}", recordId);
    }

    @Override
    public void deleteRecord(Long recordId) {
        ThrowUtils.throwIf(recordId == null, ErrorCode.PARAMS_ERROR, "生产记录ID不能为空");
        ProductionRecord exist = productionRecordMapper.selectById(recordId);
        ThrowUtils.throwIf(exist == null, ErrorCode.NOT_FOUND_ERROR, "生产记录不存在");
        productionRecordMapper.deleteById(recordId);
        log.info("[Traceability] 删除生产记录成功, id={}", recordId);
    }

    // ==================== 用户端 ====================

    @Override
    public TracePublicVO getPublicTrace(String batchNo) {
        ThrowUtils.throwIf(StrUtil.isBlank(batchNo), ErrorCode.PARAMS_ERROR, "批次号不能为空");
        TraceabilityInfo info = traceabilityInfoMapper.selectOne(
                new LambdaQueryWrapper<TraceabilityInfo>().eq(TraceabilityInfo::getBatchNo, batchNo));
        ThrowUtils.throwIf(info == null, ErrorCode.NOT_FOUND_ERROR, "溯源信息不存在");

        TracePublicVO vo = new TracePublicVO();
        vo.setProductId(info.getProductId());
        Product product = productMapper.selectById(info.getProductId());
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setProductImage(product.getMainImage());
        }
        vo.setBatchNo(info.getBatchNo());
        vo.setFarmerName(info.getFarmerName());
        vo.setFarmerPhone(info.getFarmerPhone());
        vo.setOrigin(joinOrigin(info));
        vo.setPlantingDate(info.getPlantingDate());
        vo.setHarvestDate(info.getHarvestDate());
        vo.setQualityCheckResult(info.getQualityCheckResult());
        vo.setPesticideTest(info.getPesticideTest());
        vo.setCertificationType(info.getCertificationType());
        vo.setCertificationTypeText(certificationTypeText(info.getCertificationType()));
        vo.setCertificationUrls(parseJsonList(info.getCertificationUrls()));
        vo.setFreshness(buildFreshness(info.getHarvestDate()));
        vo.setProductionRecords(productionRecordMapper.selectList(
                        new LambdaQueryWrapper<ProductionRecord>()
                                .eq(ProductionRecord::getBatchNo, info.getBatchNo())
                                .orderByAsc(ProductionRecord::getRecordDate)
                                .orderByAsc(ProductionRecord::getId))
                .stream().map(this::convertToRecordVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public ProductTraceSummaryVO getProductTraceSummary(Long productId) {
        ThrowUtils.throwIf(productId == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
        ProductTraceSummaryVO vo = new ProductTraceSummaryVO();

        TraceabilityInfo latest = traceabilityInfoMapper.selectOne(
                new LambdaQueryWrapper<TraceabilityInfo>()
                        .eq(TraceabilityInfo::getProductId, productId)
                        .orderByDesc(TraceabilityInfo::getId)
                        .last("LIMIT 1"));
        if (latest != null) {
            vo.setHasTrace(true);
            vo.setTraceBatchNo(latest.getBatchNo());
            vo.setQrCodeUrl(latest.getQrCodeUrl());
        }
        return vo;
    }

    // ==================== 私有方法 ====================

    /**
     * 查询溯源信息，不存在抛异常
     */
    private TraceabilityInfo getInfoOrThrow(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "溯源信息ID不能为空");
        TraceabilityInfo info = traceabilityInfoMapper.selectById(id);
        ThrowUtils.throwIf(info == null, ErrorCode.NOT_FOUND_ERROR, "溯源信息不存在");
        return info;
    }

    /**
     * 生成下一个批次号：B + yyyyMMdd + 4位当天序号
     * 示例：B202609110001
     */
    private String nextBatchNo() {
        String datePrefix = "B" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long count = traceabilityInfoMapper.selectCount(
                new LambdaQueryWrapper<TraceabilityInfo>().likeRight(TraceabilityInfo::getBatchNo, datePrefix));
        return datePrefix + String.format("%04d", count + 1);
    }

    /**
     * 校验新增请求
     */
    private void validateCreateRequest(TraceabilityCreateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        ThrowUtils.throwIf(request.getProductId() == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
        Product product = productMapper.selectById(request.getProductId());
        ThrowUtils.throwIf(product == null, ErrorCode.PARAMS_ERROR, "商品不存在");

        validateCommonFields(request.getFarmerName(), request.getFarmerPhone(),
                request.getQualityCheckResult(), request.getPesticideTest());
        validateCertificationType(request.getCertificationType());
        validateCertificationUrls(request.getCertificationUrls());
        validateDateRange(request.getPlantingDate(), request.getHarvestDate());

        if (request.getProductionRecords() != null) {
            for (ProductionRecordRequest record : request.getProductionRecords()) {
                validateRecordRequest(record);
            }
        }
    }

    /**
     * 校验编辑请求
     */
    private void validateUpdateRequest(TraceabilityUpdateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        ThrowUtils.throwIf(request.getProductId() == null, ErrorCode.PARAMS_ERROR, "商品ID不能为空");
        Product product = productMapper.selectById(request.getProductId());
        ThrowUtils.throwIf(product == null, ErrorCode.PARAMS_ERROR, "商品不存在");

        validateCommonFields(request.getFarmerName(), request.getFarmerPhone(),
                request.getQualityCheckResult(), request.getPesticideTest());
        validateCertificationType(request.getCertificationType());
        validateCertificationUrls(request.getCertificationUrls());
        validateDateRange(request.getPlantingDate(), request.getHarvestDate());
    }

    /**
     * 公共文本字段长度校验
     */
    private void validateCommonFields(String farmerName, String farmerPhone,
                                      String qualityCheckResult, String pesticideTest) {
        ThrowUtils.throwIf(farmerName != null && farmerName.length() > 50,
                ErrorCode.PARAMS_ERROR, "种植户姓名不能超过50个字符");
        ThrowUtils.throwIf(farmerPhone != null && farmerPhone.length() > 20,
                ErrorCode.PARAMS_ERROR, "种植户电话不能超过20个字符");
        ThrowUtils.throwIf(qualityCheckResult != null && qualityCheckResult.length() > 200,
                ErrorCode.PARAMS_ERROR, "质检结果不能超过200个字符");
        ThrowUtils.throwIf(pesticideTest != null && pesticideTest.length() > 200,
                ErrorCode.PARAMS_ERROR, "农药残留检测结果不能超过200个字符");
    }

    /**
     * 认证类型枚举校验
     */
    private void validateCertificationType(String certificationType) {
        if (certificationType == null) {
            return;
        }
        ThrowUtils.throwIf(!CERTIFICATION_TYPE_TEXT_MAP.containsKey(certificationType),
                ErrorCode.PARAMS_ERROR, "认证类型不合法");
    }

    /**
     * 认证证书图片数量校验（≤9）
     */
    private void validateCertificationUrls(List<String> urls) {
        ThrowUtils.throwIf(urls != null && urls.size() > 9,
                ErrorCode.PARAMS_ERROR, "认证证书图片最多9张");
    }

    /**
     * 日期先后校验：采摘日期不能早于种植日期
     */
    private void validateDateRange(LocalDate plantingDate, LocalDate harvestDate) {
        if (plantingDate != null && harvestDate != null && harvestDate.isBefore(plantingDate)) {
            ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "采摘日期不能早于种植日期");
        }
    }

    /**
     * 生产记录请求校验
     */
    private void validateRecordRequest(ProductionRecordRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "生产记录不能为空");
        ThrowUtils.throwIf(!RECORD_TYPE_TEXT_MAP.containsKey(request.getRecordType()),
                ErrorCode.PARAMS_ERROR, "记录类型不合法");
        ThrowUtils.throwIf(request.getRecordDate() == null,
                ErrorCode.PARAMS_ERROR, "记录日期不能为空");
        ThrowUtils.throwIf(request.getContent() != null && request.getContent().length() > 500,
                ErrorCode.PARAMS_ERROR, "记录内容不能超过500个字符");
        ThrowUtils.throwIf(request.getImages() != null && request.getImages().size() > 9,
                ErrorCode.PARAMS_ERROR, "记录图片最多9张");
        ThrowUtils.throwIf(request.getOperator() != null && request.getOperator().length() > 50,
                ErrorCode.PARAMS_ERROR, "操作人不能超过50个字符");
    }

    /**
     * 构建溯源信息实体
     */
    private TraceabilityInfo buildInfo(TraceabilityCreateRequest request) {
        TraceabilityInfo info = new TraceabilityInfo();
        info.setProductId(request.getProductId());
        info.setFarmerName(request.getFarmerName());
        info.setFarmerPhone(request.getFarmerPhone());
        info.setOriginProvince(request.getOriginProvince());
        info.setOriginCity(request.getOriginCity());
        info.setOriginDistrict(request.getOriginDistrict());
        info.setPlantingDate(request.getPlantingDate());
        info.setHarvestDate(request.getHarvestDate());
        info.setQualityCheckResult(request.getQualityCheckResult());
        info.setPesticideTest(request.getPesticideTest());
        info.setCertificationType(request.getCertificationType());
        info.setCertificationUrls(request.getCertificationUrls() == null
                ? null : JSONUtil.toJsonStr(request.getCertificationUrls()));
        return info;
    }

    /**
     * 批量插入生产记录
     */
    private void insertRecords(Long traceabilityId, Long productId, String batchNo,
                               List<ProductionRecordRequest> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<ProductionRecord> list = records.stream()
                .map(r -> buildRecord(productId, batchNo, r))
                .collect(Collectors.toList());
        productionRecordMapper.insert(list);
    }

    /**
     * 构建生产记录实体
     */
    private ProductionRecord buildRecord(Long productId, String batchNo, ProductionRecordRequest request) {
        ProductionRecord record = new ProductionRecord();
        record.setProductId(productId);
        record.setBatchNo(batchNo);
        record.setRecordType(request.getRecordType());
        record.setRecordDate(request.getRecordDate());
        record.setContent(request.getContent());
        record.setImages(request.getImages() == null ? null : JSONUtil.toJsonStr(request.getImages()));
        record.setOperator(request.getOperator());
        return record;
    }

    /**
     * 生产记录 → VO
     */
    private ProductionRecordVO convertToRecordVO(ProductionRecord record) {
        ProductionRecordVO vo = new ProductionRecordVO();
        vo.setId(record.getId());
        vo.setRecordType(record.getRecordType());
        vo.setRecordTypeText(recordTypeText(record.getRecordType()));
        vo.setRecordDate(record.getRecordDate());
        vo.setContent(record.getContent());
        vo.setImages(parseJsonList(record.getImages()));
        vo.setOperator(record.getOperator());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /**
     * 产地拼接：省 + 市 + 区（跳过空值）
     */
    private String joinOrigin(TraceabilityInfo info) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(info.getOriginProvince())) {
            sb.append(info.getOriginProvince());
        }
        if (StrUtil.isNotBlank(info.getOriginCity())) {
            sb.append(info.getOriginCity());
        }
        if (StrUtil.isNotBlank(info.getOriginDistrict())) {
            sb.append(info.getOriginDistrict());
        }
        return sb.toString();
    }

    /**
     * JSON 数组字符串 → List，解析失败返回空列表
     */
    private List<String> parseJsonList(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            log.warn("[Traceability] JSON数组解析失败: {}", json);
            return new ArrayList<>();
        }
    }

    /**
     * 新鲜度计算：按距采摘天数分档
     * FRESH=现摘现发(≤3天) NORMAL=新鲜(4~7天) FAIR=较新鲜(8~15天) STALE=建议尽快食用(>15天)
     */
    private TracePublicVO.FreshnessVO buildFreshness(LocalDate harvestDate) {
        if (harvestDate == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(harvestDate, LocalDate.now());
        TracePublicVO.FreshnessVO freshness = new TracePublicVO.FreshnessVO();
        freshness.setDaysSinceHarvest(days);
        if (days <= 3) {
            freshness.setLevel("FRESH");
            freshness.setLevelText("现摘现发");
        } else if (days <= 7) {
            freshness.setLevel("NORMAL");
            freshness.setLevelText("新鲜");
        } else if (days <= 15) {
            freshness.setLevel("FAIR");
            freshness.setLevelText("较新鲜");
        } else {
            freshness.setLevel("STALE");
            freshness.setLevelText("建议尽快食用");
        }
        return freshness;
    }
}
