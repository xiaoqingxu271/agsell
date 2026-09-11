package com.lichun.agsell.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.lichun.agsell.config.AliyunOssProperties;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    private final OSS ossClient;
    private final AliyunOssProperties ossProperties;

    @Override
    public String upload(MultipartFile file, String prefix, FileType fileType) {
        // 1. 非空校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "上传文件不能为空");
        }

        // 2. 文件名安全校验（防止路径穿越）
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(ErrorCode.FILE_NAME_INVALID, "文件名不能为空");
        }
        if (originalFilename.contains("..") || originalFilename.contains("/")) {
            throw new BusinessException(ErrorCode.FILE_NAME_INVALID, "文件名不合法");
        }

        // 2.1 存储目录前缀安全校验（防止路径穿越与越权目录）
        if (!StringUtils.hasText(prefix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "存储目录不能为空");
        }
        if (prefix.contains("..") || prefix.startsWith("/") || prefix.length() > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "存储目录不合法");
        }

        // 3. 大小校验
        long maxSizeBytes = getFileSizeLimit(fileType);
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED,
                    String.format("文件大小不能超过 %dMB", maxSizeBytes / 1024 / 1024));
        }

        // 4. 文件类型校验
        String extension = getExtension(originalFilename);
        List<String> allowedTypes = getAllowedTypes(fileType);
        if (!allowedTypes.contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    String.format("不支持的文件类型：%s，允许的类型：%s",
                            extension, String.join(",", allowedTypes)));
        }

        // 4.1 图片内容魔数校验（防止改后缀绕过白名单）
        if (fileType == FileType.IMAGE) {
            try {
                validateImageMagic(file);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "图片内容校验失败");
            }
        }

        // 5. 生成唯一对象名：prefix/yyyyMMdd/uuid.extension
        String datePath = java.time.LocalDate.now().toString().replace("-", "");
        String uniqueName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String objectKey = prefix + "/" + datePath + "/" + uniqueName;

        try {
            // 6. 执行上传（公共读，否则直接访问 URL 会返回 403）
            com.aliyun.oss.model.ObjectMetadata objectMetadata = new com.aliyun.oss.model.ObjectMetadata();
            objectMetadata.setObjectAcl(CannedAccessControlList.PublicRead);
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    ossProperties.getBucketName(),
                    objectKey,
                    file.getInputStream(),
                    objectMetadata
            );
            PutObjectResult result = ossClient.putObject(putObjectRequest);
            log.info("文件上传成功，objectKey={}, etag={}", objectKey, result.getETag());

            // 7. 拼接访问 URL
            return buildFileUrl(objectKey);

        } catch (IOException e) {
            log.error("文件上传失败，objectKey={}", objectKey, e);
            throw new BusinessException(ErrorCode.OSS_CLIENT_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public String uploadBytes(byte[] data, String objectKey, String contentType) {
        // 1. 非空校验
        if (data == null || data.length == 0) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "上传数据不能为空");
        }

        // 2. 存储对象路径安全校验（防止路径穿越与越权目录）
        if (!StringUtils.hasText(objectKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "存储对象路径不能为空");
        }
        if (objectKey.contains("..") || objectKey.startsWith("/") || objectKey.length() > 200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "存储对象路径不合法");
        }

        try {
            // 3. 执行上传（公共读，否则直接访问 URL 会返回 403）
            com.aliyun.oss.model.ObjectMetadata objectMetadata = new com.aliyun.oss.model.ObjectMetadata();
            objectMetadata.setContentType(contentType);
            objectMetadata.setContentLength(data.length);
            objectMetadata.setObjectAcl(CannedAccessControlList.PublicRead);
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    ossProperties.getBucketName(),
                    objectKey,
                    new java.io.ByteArrayInputStream(data),
                    objectMetadata
            );
            PutObjectResult result = ossClient.putObject(putObjectRequest);
            log.info("字节数据上传成功，objectKey={}, etag={}", objectKey, result.getETag());

            // 4. 拼接访问 URL
            return buildFileUrl(objectKey);

        } catch (Exception e) {
            log.error("字节数据上传失败，objectKey={}", objectKey, e);
            throw new BusinessException(ErrorCode.OSS_CLIENT_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public boolean delete(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return false;
        }
        try {
            String objectKey = extractObjectKey(fileUrl);
            if (objectKey == null) {
                return false;
            }
            ossClient.deleteObject(ossProperties.getBucketName(), objectKey);
            log.info("文件删除成功，objectKey={}", objectKey);
            return true;
        } catch (Exception e) {
            log.error("文件删除失败，fileUrl={}", fileUrl, e);
            return false;
        }
    }

    // ========== 私有方法 ==========

    /**
     * 根据文件类型返回大小限制（字节）
     */
    private long getFileSizeLimit(FileType fileType) {
        return switch (fileType) {
            case IMAGE -> ossProperties.getImageMaxFileSize() * 1024L * 1024;
            default -> ossProperties.getMaxFileSize() * 1024L * 1024;
        };
    }

    /**
     * 根据文件类型返回允许的后缀列表
     */
    private List<String> getAllowedTypes(FileType fileType) {
        return switch (fileType) {
            case IMAGE -> Arrays.asList(ossProperties.getAllowedImageTypes().split(","));
            case VIDEO -> Arrays.asList(ossProperties.getAllowedVideoTypes().split(","));
            default -> Arrays.asList(ossProperties.getAllowedOtherTypes().split(","));
        };
    }

    /**
     * 常见图片格式魔数（文件头部字节）
     */
    private static final byte[][] IMAGE_MAGIC = {
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},            // jpg/jpeg
            {(byte) 0x89, 0x50, 0x4E, 0x47},                    // png
            {0x47, 0x49, 0x46, 0x38},                           // gif
            {0x42, 0x4D},                                       // bmp
            {(byte) 0x52, 0x49, 0x46, 0x46}                     // webp(RIFF)，需再校验 WEBP
    };

    /**
     * 校验图片文件头魔数，防止改后缀绕过类型白名单
     */
    private void validateImageMagic(MultipartFile file) throws IOException {
        byte[] header = new byte[12];
        int read = file.getInputStream().read(header);
        if (read < 2) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "图片内容为空或损坏");
        }
        for (byte[] magic : IMAGE_MAGIC) {
            if (read >= magic.length && startsWith(header, magic)) {
                // webp 需要额外校验第 8-12 字节为 "WEBP"
                if (magic[0] == (byte) 0x52 && magic[1] == 0x49) {
                    if (read >= 12
                            && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                        return;
                    }
                    continue;
                }
                return;
            }
        }
        throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "图片内容与文件类型不符");
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取文件扩展名（不含点）
     */
    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex >= 0 && dotIndex < filename.length() - 1)
                ? filename.substring(dotIndex + 1)
                : "";
    }

    /**
     * 构建文件访问 URL
     * 优先使用自定义 CDN 域名，否则使用 OSS 默认域名
     */
    private String buildFileUrl(String objectKey) {
        String baseUrl = StringUtils.hasText(ossProperties.getUrlPrefix())
                ? ossProperties.getUrlPrefix()
                : "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint();
        return baseUrl + "/" + objectKey;
    }

    /**
     * 从完整 URL 中提取 objectKey
     * 例如：https://cdn.example.com/user/avatar/20260904/abc123.jpg → user/avatar/20260904/abc123.jpg
     */
    private String extractObjectKey(String fileUrl) {
        String urlPrefix = ossProperties.getUrlPrefix();
        if (StringUtils.hasText(urlPrefix) && fileUrl.startsWith(urlPrefix)) {
            return fileUrl.substring(urlPrefix.length()).replaceFirst("^/", "");
        }
        // 回退：尝试从默认域名提取
        String defaultDomain = ossProperties.getBucketName() + "." + ossProperties.getEndpoint();
        int idx = fileUrl.indexOf(defaultDomain);
        if (idx >= 0) {
            return fileUrl.substring(idx + defaultDomain.length()).replaceFirst("^/", "");
        }
        return null;
    }
}
