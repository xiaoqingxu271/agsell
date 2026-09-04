package com.lichun.agsell.config;

import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Jackson 全局精度配置
 * Long、long、BigDecimal 统一序列化为 String，解决 JSON 序列化时精度丢失问题
 * Snowflake ID (19位) 超过 JS Number.MAX_SAFE_INTEGER (2^53-1 = 9007199254740991)
 *
 * Spring Boot 4.1+ 使用 Eclipse Foundation Jackson 3.x (tools.jackson)
 */
@Configuration
public class JacksonConfig {

    /**
     * Long包装类型、long，BigDecimal基本类型统一序列化为字符串
     */
    @Bean
    public SimpleModule longConvertModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
        return module;
    }
}
