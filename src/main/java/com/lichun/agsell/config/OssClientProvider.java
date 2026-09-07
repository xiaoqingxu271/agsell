package com.lichun.agsell.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 客户端单例管理
 * Spring 容器启动时初始化，全局复用同一个 OSS 实例
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OssClientProvider {

    private final AliyunOssProperties ossProperties;

    @Bean
    public OSS ossClient() {
        log.info("初始化阿里云 OSS 客户端，endpoint={}, bucket={}",
                ossProperties.getEndpoint(), ossProperties.getBucketName());
        return new OSSClientBuilder()
                .build(ossProperties.getEndpoint(),
                        ossProperties.getAccessKeyId(),
                        ossProperties.getAccessKeySecret());
    }
}
