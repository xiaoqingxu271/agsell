package com.lichun.agsell.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 注册 JWT 拦截器 + AI 端点限流拦截器 + AI 内部接口拦截器 + CORS 跨域配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final AiInternalAuthInterceptor aiInternalAuthInterceptor;
    private final AiRateLimitInterceptor aiRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**");
        // AI 端点额度保护：注册在 JWT 拦截器之后，才能复用它解析出的 userId（BaseContext）
        registry.addInterceptor(aiRateLimitInterceptor)
                .addPathPatterns("/ai/chat", "/ai/chat/**",
                        "/product/recommend/feed", "/product/view");
        // AI 内部接口：JWT 白名单放行后，再由本拦截器校验 X-Internal-Key
        // 注意：context-path=/api，拦截器 pathPattern 匹配的是 servlet path（不含 context-path）
        registry.addInterceptor(aiInternalAuthInterceptor)
                .addPathPatterns("/ai/internal/**");
    }

    /**
     * CORS 跨域配置。
     * <p>默认只放行本机来源（管理后台/H5 调试端口）：小程序原生请求不带 Origin、不受 CORS 约束，
     * 线上 H5 与后台都由 nginx 同源反代 /api，同样不需要跨域。
     * <p>确需跨域时用环境变量 CORS_ALLOWED_ORIGINS 指定具体域名（逗号分隔）。
     * 不要改回 "*"/全通配：allowedOriginPattern("*") 叠加 allowCredentials(true)
     * 等于允许任意第三方站点带凭证读取接口响应。
     */
    @org.springframework.beans.factory.annotation.Value(
            "${cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOrigins;

    @org.springframework.context.annotation.Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .forEach(config::addAllowedOriginPattern);
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("Authorization");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 必须注册 "/**" 而不是 "/api/**"：本服务有 server.context-path=/api，
        // UrlBasedCorsConfigurationSource 匹配的是 getPathWithinApplication()——已经去掉 context-path
        // 的路径（如 "/system/config"）。写 "/api/**" 会一条都不命中，结果是对任何 Origin
        // 都不回 Access-Control-Allow-Origin 头，跨域预检全部静默失败。
        // 此前没暴露是因为 H5 调试走 vite 同源代理、小程序请求不受 CORS 约束。
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
