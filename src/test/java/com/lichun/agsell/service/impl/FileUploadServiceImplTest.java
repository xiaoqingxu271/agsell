package com.lichun.agsell.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.lichun.agsell.config.AliyunOssProperties;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.service.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 文件上传加固验证：目录前缀校验、图片魔数校验、OTHER 类型白名单
 */
class FileUploadServiceImplTest {

    private OSS ossClient;
    private AliyunOssProperties ossProperties;
    private FileUploadServiceImpl fileUploadService;

    @BeforeEach
    void setUp() {
        ossClient = mock(OSS.class);
        ossProperties = new AliyunOssProperties();
        ossProperties.setBucketName("agsell-bucket");
        ossProperties.setEndpoint("oss-cn-beijing.aliyuncs.com");
        ossProperties.setUrlPrefix("");
        ossProperties.setMaxFileSize(10);
        ossProperties.setImageMaxFileSize(5);
        ossProperties.setAllowedImageTypes("jpg,jpeg,png,gif,webp,bmp");
        ossProperties.setAllowedVideoTypes("mp4,avi,mov,wmv");
        ossProperties.setAllowedOtherTypes("pdf,doc,docx,xls,xlsx,ppt,pptx,zip,txt");
        fileUploadService = new FileUploadServiceImpl(ossClient, ossProperties);
    }

    private MockMultipartFile pngFile(String filename) {
        // PNG 魔数：89 50 4E 47 0D 0A 1A 0A
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00};
        return new MockMultipartFile("file", filename, "image/png", pngHeader);
    }

    @Test
    @DisplayName("空文件上传失败")
    void uploadEmptyFileFails() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.upload(file, "product/image", FileUploadService.FileType.IMAGE));
        assertEquals(ErrorCode.FILE_UPLOAD_FAILED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("文件名含路径穿越字符被拒绝")
    void uploadWithTraversalFilenameFails() {
        MockMultipartFile file = new MockMultipartFile("file", "../a.png", "image/png", new byte[]{1, 2, 3});
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.upload(file, "product/image", FileUploadService.FileType.IMAGE));
        assertEquals(ErrorCode.FILE_NAME_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("存储目录为空被拒绝")
    void uploadWithBlankPrefixFails() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.upload(pngFile("a.png"), " ", FileUploadService.FileType.IMAGE));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("存储目录含路径穿越被拒绝")
    void uploadWithTraversalPrefixFails() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.upload(pngFile("a.png"), "../product/image", FileUploadService.FileType.IMAGE));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("图片超过大小限制被拒绝")
    void uploadOversizeImageFails() {
        byte[] big = new byte[6 * 1024 * 1024];
        big[0] = (byte) 0x89;
        big[1] = 0x50;
        big[2] = 0x4E;
        big[3] = 0x47;
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", big);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.upload(file, "product/image", FileUploadService.FileType.IMAGE));
        assertEquals(ErrorCode.FILE_SIZE_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非图片后缀上传为图片类型被拒绝")
    void uploadNonImageExtensionFails() {
        MockMultipartFile file = new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[]{1});
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.upload(file, "product/image", FileUploadService.FileType.IMAGE));
        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("改后缀的文本文件（魔数不符）被拒绝")
    void uploadSpoofedImageFails() {
        // 内容为纯文本却伪装 .png
        byte[] text = "not a real image".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", text);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.upload(file, "product/image", FileUploadService.FileType.IMAGE));
        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("合法 PNG 上传成功并生成随机文件名")
    void uploadValidPngSucceeds() {
        when(ossClient.putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());

        String url = fileUploadService.upload(pngFile("photo.png"), "product/image", FileUploadService.FileType.IMAGE);

        assertTrue(url.startsWith("https://agsell-bucket.oss-cn-beijing.aliyuncs.com/product/image/"));
        assertTrue(url.endsWith(".png"));
        // 文件名必须是 UUID，不能保留原始文件名
        assertFalse(url.contains("photo"));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(ossClient).putObject(captor.capture());
        assertTrue(captor.getValue().getKey().startsWith("product/image/"));
    }

    @Test
    @DisplayName("OTHER 类型支持文档白名单（修复后可用）")
    void uploadOtherDocSucceeds() {
        when(ossClient.putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());

        MockMultipartFile file = new MockMultipartFile("file", "spec.pdf", "application/pdf", new byte[]{1, 2, 3});
        String url = fileUploadService.upload(file, "doc/attach", FileUploadService.FileType.OTHER);

        assertTrue(url.endsWith(".pdf"));
        verify(ossClient).putObject(any(PutObjectRequest.class));
    }
}
