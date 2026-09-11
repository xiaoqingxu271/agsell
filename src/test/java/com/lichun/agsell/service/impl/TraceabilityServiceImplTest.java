package com.lichun.agsell.service.impl;

import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductionRecordMapper;
import com.lichun.agsell.mapper.TraceabilityInfoMapper;
import com.lichun.agsell.model.dto.ProductionRecordRequest;
import com.lichun.agsell.model.dto.TraceabilityCreateRequest;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.entity.ProductionRecord;
import com.lichun.agsell.model.entity.TraceabilityInfo;
import com.lichun.agsell.model.vo.ProductTraceSummaryVO;
import com.lichun.agsell.model.vo.TracePublicVO;
import com.lichun.agsell.model.vo.TraceabilityCreateResultVO;
import com.lichun.agsell.service.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 产地溯源模块单元测试：批次号生成、新增/级联删除、二维码生成、枚举校验、新鲜度
 */
@ExtendWith(MockitoExtension.class)
class TraceabilityServiceImplTest {

    private static final Pattern BATCH_NO_PATTERN = Pattern.compile("^B\\d{12}$");

    @Mock
    private TraceabilityInfoMapper traceabilityInfoMapper;
    @Mock
    private ProductionRecordMapper productionRecordMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private FileUploadService fileUploadService;

    @InjectMocks
    private TraceabilityServiceImpl traceabilityService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(traceabilityService, "traceBaseUrl", "http://localhost:8080/api");
    }

    private Product mockProduct() {
        Product product = new Product();
        product.setId(1001L);
        product.setName("赣南脐橙");
        product.setMainImage("https://oss.example.com/main.png");
        return product;
    }

    private TraceabilityCreateRequest buildCreateRequest() {
        TraceabilityCreateRequest request = new TraceabilityCreateRequest();
        request.setProductId(1001L);
        request.setFarmerName("李四");
        request.setOriginProvince("江西省");
        request.setOriginCity("赣州市");
        request.setOriginDistrict("信丰县");
        request.setPlantingDate(LocalDate.of(2026, 3, 10));
        request.setHarvestDate(LocalDate.of(2026, 9, 8));
        request.setQualityCheckResult("抽检合格");
        request.setPesticideTest("农残未检出");
        request.setCertificationType("GEOGRAPHICAL");

        ProductionRecordRequest record = new ProductionRecordRequest();
        record.setRecordType("SEEDING");
        record.setRecordDate(LocalDate.of(2026, 3, 10));
        record.setContent("完成定植，株距 3m");
        record.setOperator("李四");
        request.setProductionRecords(List.of(record));
        return request;
    }

    @Test
    @DisplayName("新增溯源信息成功：批次号格式正确、生产记录批量入库")
    void createTraceability_success() {
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        // mock insert：模拟 MyBatis-Plus 雪花 ID 回填
        doAnswer(invocation -> {
            invocation.getArgument(0, TraceabilityInfo.class).setId(1L);
            return 1;
        }).when(traceabilityInfoMapper).insert(any(TraceabilityInfo.class));

        TraceabilityCreateResultVO result = traceabilityService.createTraceability(buildCreateRequest());

        assertNotNull(result.getId());
        assertTrue(BATCH_NO_PATTERN.matcher(result.getBatchNo()).matches(),
                "批次号应为 B + yyyyMMdd + 4位序号，实际：" + result.getBatchNo());

        // 验证溯源信息落库批次号
        ArgumentCaptor<TraceabilityInfo> infoCaptor = ArgumentCaptor.forClass(TraceabilityInfo.class);
        verify(traceabilityInfoMapper).insert((TraceabilityInfo) infoCaptor.capture());
        assertEquals(result.getBatchNo(), infoCaptor.getValue().getBatchNo());
        assertEquals(1001L, infoCaptor.getValue().getProductId());

        // 验证生产记录批量入库且批次号一致
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductionRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionRecordMapper).insert(recordsCaptor.capture());
        assertEquals(1, recordsCaptor.getValue().size());
        assertEquals(result.getBatchNo(), recordsCaptor.getValue().get(0).getBatchNo());
        assertEquals("SEEDING", recordsCaptor.getValue().get(0).getRecordType());
    }

    @Test
    @DisplayName("新增溯源信息：商品不存在抛参数错误")
    void createTraceability_productNotExist() {
        when(productMapper.selectById(1001L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> traceabilityService.createTraceability(buildCreateRequest()));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(traceabilityInfoMapper, never()).insert(any(TraceabilityInfo.class));
    }

    @Test
    @DisplayName("新增溯源信息：采摘日期早于种植日期抛参数错误")
    void createTraceability_invalidDateRange() {
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        TraceabilityCreateRequest request = buildCreateRequest();
        request.setPlantingDate(LocalDate.of(2026, 9, 1));
        request.setHarvestDate(LocalDate.of(2026, 3, 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> traceabilityService.createTraceability(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("新增溯源信息：非法认证类型抛参数错误")
    void createTraceability_invalidCertificationType() {
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        TraceabilityCreateRequest request = buildCreateRequest();
        request.setCertificationType("FAKE_TYPE");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> traceabilityService.createTraceability(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("同一天连续新增：批次号序号递增不重复")
    void createTraceability_batchNoIncrement() {
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        // 第一次：当天批次计数 0 → 0001；第二次：计数 1 → 0002
        when(traceabilityInfoMapper.selectCount(any()))
                .thenReturn(0L, 1L);

        TraceabilityCreateResultVO first = traceabilityService.createTraceability(buildCreateRequest());
        TraceabilityCreateResultVO second = traceabilityService.createTraceability(buildCreateRequest());

        assertNotEquals(first.getBatchNo(), second.getBatchNo());
        String prefix = first.getBatchNo().substring(0, first.getBatchNo().length() - 4);
        assertTrue(second.getBatchNo().startsWith(prefix), "同一天批次号前缀应一致");
        assertEquals("0002", second.getBatchNo().substring(second.getBatchNo().length() - 4));
    }

    @Test
    @DisplayName("删除溯源信息：级联逻辑删除该批次生产记录")
    void deleteTraceability_cascadeDeleteRecords() {
        TraceabilityInfo info = new TraceabilityInfo();
        info.setId(1L);
        info.setProductId(1001L);
        info.setBatchNo("B202609110001");
        when(traceabilityInfoMapper.selectById(1L)).thenReturn(info);

        traceabilityService.deleteTraceability(1L);

        ArgumentCaptor<Object> recordsWrapper = ArgumentCaptor.forClass(Object.class);
        verify(productionRecordMapper).delete(any());
        verify(traceabilityInfoMapper).deleteById(1L);
    }

    @Test
    @DisplayName("生成二维码：PNG 上传 OSS、objectKey 含批次号、qrCodeUrl 落库")
    void generateQrCode_success() {
        TraceabilityInfo info = new TraceabilityInfo();
        info.setId(1L);
        info.setProductId(1001L);
        info.setBatchNo("B202609110001");
        when(traceabilityInfoMapper.selectById(1L)).thenReturn(info);
        when(fileUploadService.uploadBytes(any(byte[].class), anyString(), anyString()))
                .thenReturn("https://oss.example.com/trace/qr/B202609110001.png");

        String url = traceabilityService.generateQrCode(1L);

        assertEquals("https://oss.example.com/trace/qr/B202609110001.png", url);
        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileUploadService).uploadBytes(any(byte[].class), objectKeyCaptor.capture(), contentTypeCaptor.capture());
        assertEquals("trace/qr/B202609110001.png", objectKeyCaptor.getValue());
        assertEquals("image/png", contentTypeCaptor.getValue());

        // 验证 qrCodeUrl 落库
        ArgumentCaptor<TraceabilityInfo> updateCaptor = ArgumentCaptor.forClass(TraceabilityInfo.class);
        verify(traceabilityInfoMapper).updateById((TraceabilityInfo) updateCaptor.capture());
        assertEquals(url, updateCaptor.getValue().getQrCodeUrl());
    }

    @Test
    @DisplayName("生成二维码：OSS 上传失败原样抛业务异常")
    void generateQrCode_uploadFailed() {
        TraceabilityInfo info = new TraceabilityInfo();
        info.setId(1L);
        info.setBatchNo("B202609110001");
        when(traceabilityInfoMapper.selectById(1L)).thenReturn(info);
        when(fileUploadService.uploadBytes(any(byte[].class), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.OSS_CLIENT_ERROR, "上传失败"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> traceabilityService.generateQrCode(1L));
        assertEquals(ErrorCode.OSS_CLIENT_ERROR.getCode(), ex.getCode());
        verify(traceabilityInfoMapper, never()).updateById(any(TraceabilityInfo.class));
    }

    @Test
    @DisplayName("用户端溯源档案：按采摘天数计算新鲜度（≤3天=现摘现发）")
    void getPublicTrace_freshnessFresh() {
        TraceabilityInfo info = new TraceabilityInfo();
        info.setId(1L);
        info.setProductId(1001L);
        info.setBatchNo("B202609110001");
        info.setHarvestDate(LocalDate.now().minusDays(2));
        when(traceabilityInfoMapper.selectOne(any())).thenReturn(info);
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(productionRecordMapper.selectList(any())).thenReturn(List.of());

        TracePublicVO vo = traceabilityService.getPublicTrace("B202609110001");

        assertNotNull(vo.getFreshness());
        assertEquals("FRESH", vo.getFreshness().getLevel());
        assertEquals("现摘现发", vo.getFreshness().getLevelText());
        assertEquals(2L, vo.getFreshness().getDaysSinceHarvest());
    }

    @Test
    @DisplayName("用户端溯源档案：采摘超过15天标记建议尽快食用")
    void getPublicTrace_freshnessStale() {
        TraceabilityInfo info = new TraceabilityInfo();
        info.setId(1L);
        info.setProductId(1001L);
        info.setBatchNo("B202609110001");
        info.setHarvestDate(LocalDate.now().minusDays(20));
        when(traceabilityInfoMapper.selectOne(any())).thenReturn(info);
        when(productMapper.selectById(1001L)).thenReturn(mockProduct());
        when(productionRecordMapper.selectList(any())).thenReturn(List.of());

        TracePublicVO vo = traceabilityService.getPublicTrace("B202609110001");

        assertEquals("STALE", vo.getFreshness().getLevel());
        assertEquals("建议尽快食用", vo.getFreshness().getLevelText());
    }

    @Test
    @DisplayName("用户端溯源档案：批次号为空抛参数错误")
    void getPublicTrace_blankBatchNo() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> traceabilityService.getPublicTrace("  "));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("用户端溯源档案：批次号不存在抛未找到")
    void getPublicTrace_notFound() {
        when(traceabilityInfoMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> traceabilityService.getPublicTrace("B202609119999"));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("商品溯源摘要：无溯源信息返回 hasTrace=false")
    void getProductTraceSummary_noTrace() {
        when(traceabilityInfoMapper.selectOne(any())).thenReturn(null);

        ProductTraceSummaryVO vo = traceabilityService.getProductTraceSummary(1001L);

        assertFalse(vo.isHasTrace());
        assertNull(vo.getTraceBatchNo());
    }

    @Test
    @DisplayName("商品溯源摘要：存在溯源返回最新批次号")
    void getProductTraceSummary_hasTrace() {
        TraceabilityInfo info = new TraceabilityInfo();
        info.setId(1L);
        info.setProductId(1001L);
        info.setBatchNo("B202609110001");
        when(traceabilityInfoMapper.selectOne(any())).thenReturn(info);

        ProductTraceSummaryVO vo = traceabilityService.getProductTraceSummary(1001L);

        assertTrue(vo.isHasTrace());
        assertEquals("B202609110001", vo.getTraceBatchNo());
    }

    @Test
    @DisplayName("新增生产记录：非法记录类型抛参数错误")
    void createRecord_invalidRecordType() {
        TraceabilityInfo info = new TraceabilityInfo();
        info.setId(1L);
        info.setProductId(1001L);
        info.setBatchNo("B202609110001");
        when(traceabilityInfoMapper.selectById(1L)).thenReturn(info);

        ProductionRecordRequest request = new ProductionRecordRequest();
        request.setTraceabilityId(1L);
        request.setRecordType("FAKE_TYPE");
        request.setRecordDate(LocalDate.now());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> traceabilityService.createRecord(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }
}
