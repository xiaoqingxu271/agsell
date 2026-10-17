package com.lichun.agsell.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CORS 预检回归测试（真实 servlet context-path 下跑）。
 * <p>
 * 为什么必须起真容器（RANDOM_PORT）+ 真 HTTP 客户端，而不用 MockMvc：本服务有
 * server.context-path=/api，而 UrlBasedCorsConfigurationSource 匹配的是
 * "去掉 context-path 之后的路径"。曾把规则注册成 "/api/**"，MockMvc 那种不带
 * context-path 的测试会照常通过，真机上却对任何 Origin 都不回 Access-Control-Allow-Origin
 * 头——所有跨域调试（H5 直连后端、第三方页面嵌接口）静默失败。
 * 这里刻意带 /api 前缀发预检，把这个坑钉住。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsPreflightTest {

    @LocalServerPort
    private int port;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            // 不自动跟随重定向：预检响应必须原样拿到 CORS 头
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** 发一次跨域预检，返回响应头 */
    private HttpResponse<Void> preflight(String origin) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/system/config?keys=platform_name"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .timeout(Duration.ofSeconds(10))
                .build();
        return CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
    }

    private static List<String> header(HttpResponse<Void> resp, String name) {
        // allValues 缺失时返回空列表，不必区分"没有这个头"与"值为空"——断言只看有没有放行值
        return resp.headers().allValues(name);
    }

    @Test
    @DisplayName("带 Origin 的预检必须拿到 Access-Control-Allow-Origin（本机来源在默认白名单内）")
    void preflightReturnsCorsHeaders() throws Exception {
        HttpResponse<Void> resp = preflight("http://127.0.0.1:8081");

        assertEquals(200, resp.statusCode(), "预检应正常返回");
        List<String> allowOrigin = header(resp, "access-control-allow-origin");
        assertTrue(allowOrigin.contains("http://127.0.0.1:8081"),
                "CORS 规则必须注册为 /**（context-path=/api 会先被剥掉），否则这里拿不到放行头，"
                        + "实际响应头=" + resp.headers().map());
        assertTrue(header(resp, "access-control-allow-credentials").contains("true"),
                "白名单来源应允许携带凭证（Authorization 头）");
    }

    @Test
    @DisplayName("白名单外的 Origin 不得拿到放行头（禁止 credentials + 通配的宽松组合）")
    void preflightFromUnknownOriginGetsNoAllowHeader() throws Exception {
        List<String> allowOrigin = header(preflight("https://evil.example.com"), "access-control-allow-origin");
        assertTrue(allowOrigin.stream().noneMatch(o -> o.startsWith("http")),
                "第三方站点不得带凭证读取接口响应，实际回：" + allowOrigin);
    }
}
