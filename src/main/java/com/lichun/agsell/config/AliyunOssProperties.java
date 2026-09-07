package com.lichun.agsell.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

    /** OSS 外网端点，例如 oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;

    /** 访问密钥 ID */
    private String accessKeyId;

    /** 访问密钥 Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** 自定义域名前缀（CDN 域名），为空时使用 endpoint + bucketName 拼接 */
    private String urlPrefix;

    /** 单文件最大大小（MB） */
    private long maxFileSize = 10;

    /** 图片单文件最大大小（MB） */
    private long imageMaxFileSize = 5;

    /** 允许的图片文件扩展名（不含点，逗号分隔） */
    private String allowedImageTypes = "jpg,jpeg,png,gif,webp,bmp";

    /** 允许的视频文件扩展名（不含点，逗号分隔） */
    private String allowedVideoTypes = "mp4,avi,mov,wmv";
}
