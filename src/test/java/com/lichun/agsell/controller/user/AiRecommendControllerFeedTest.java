package com.lichun.agsell.controller.user;

import com.lichun.agsell.model.vo.ProductListItemVO;
import com.lichun.agsell.service.ProductService;
import com.lichun.agsell.utils.UserContextResolver;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 首页推荐 feed 的整页保障：AI 命中不足 limit 时用热销榜补齐，且不给补齐项编造理由
 * <p>
 * 用 JDK 自带 HttpServer 顶住 /v1/rec/feed，走真实的 JSON 解析与降级分支（不起 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRecommendControllerFeedTest {

    @Mock
    private ProductService productService;
    @Mock
    private UserContextResolver userContextResolver;

    private HttpServer server;
    private AiRecommendController controller;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void stubAiFeed(String itemsJson) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rec/feed", exchange -> {
            byte[] body = ("{\"items\":" + itemsJson + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        controller = new AiRecommendController(productService, userContextResolver);
        ReflectionTestUtils.setField(controller, "aiBaseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(controller, "internalKey", "k");
    }

    private ProductListItemVO vo(long id, String categoryName) {
        ProductListItemVO vo = new ProductListItemVO();
        vo.setId(id);
        vo.setName("商品" + id);
        vo.setCategoryName(categoryName);
        return vo;
    }

    private List<Long> ids(List<ProductListItemVO> list) {
        return list.stream().map(ProductListItemVO::getId).collect(Collectors.toList());
    }

    @Test
    @DisplayName("AI 只给 2 条、limit=4：热销榜补齐到整页，补齐项不挂理由")
    void underfilledFeedIsBackfilledByHotList() throws Exception {
        stubAiFeed("[{\"id\":7,\"signals\":{\"sem\":0,\"pref\":0,\"sales\":0.1,\"rate\":1.0,\"rate_conf\":1.0,\"fresh\":0,\"browse\":0}},"
                + "{\"id\":8,\"signals\":{\"sem\":0,\"pref\":0,\"sales\":0.9,\"rate\":0.2,\"rate_conf\":1.0,\"fresh\":0,\"browse\":0}}]");
        when(userContextResolver.resolveUserId(org.mockito.ArgumentMatchers.any())).thenReturn(7L);
        when(productService.listProductsByIds(eq(List.of(7L, 8L))))
                .thenReturn(new ArrayList<>(List.of(vo(7, "水果"), vo(8, "粮油米面"))));
        // 热销榜里 7 已在页面内，补齐应跳过重复项
        when(productService.listHotProducts(4)).thenReturn(new ArrayList<>(
                List.of(vo(7, "水果"), vo(1, "蔬菜"), vo(2, "水产"), vo(3, "茶叶"))));

        List<ProductListItemVO> result = controller.feed(4, new MockHttpServletRequest()).getData();

        assertEquals(List.of(7L, 8L, 1L, 2L), ids(result));
        assertEquals("好评率高", result.get(0).getRecommendReason());   // 7 的信号里 rate 最强
        assertEquals("热销爆款", result.get(1).getRecommendReason());   // 8 的信号里 sales 最强
        assertNull(result.get(2).getRecommendReason());                 // 热销补齐项没有 AI 信号，不编造理由
        assertNull(result.get(3).getRecommendReason());
    }

    @Test
    @DisplayName("零评价商品的 rate 只是全站先验：feed 出口不谎称'好评率高'，退回销量口径")
    void priorRateNeverClaimedAsHighRating() throws Exception {
        stubAiFeed("[{\"id\":9,\"signals\":{\"sem\":0,\"pref\":0,\"sales\":0.2,"
                + "\"rate\":0.6667,\"rate_conf\":0.0,\"fresh\":0,\"browse\":0}}]");
        when(userContextResolver.resolveUserId(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(productService.listProductsByIds(eq(List.of(9L))))
                .thenReturn(new ArrayList<>(List.of(vo(9, "水果"))));

        List<ProductListItemVO> result = controller.feed(1, new MockHttpServletRequest()).getData();

        assertEquals(List.of(9L), ids(result));
        assertEquals("热销爆款", result.get(0).getRecommendReason());
    }

    @Test
    @DisplayName("AI 返回空 items：整页热销兜底，不做二次补齐")
    void emptyAiPayloadFallsBackToHotListWholesale() throws Exception {
        stubAiFeed("[]");
        when(userContextResolver.resolveUserId(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(productService.listHotProducts(6)).thenReturn(new ArrayList<>(List.of(vo(1, "蔬菜"))));

        List<ProductListItemVO> result = controller.feed(6, new MockHttpServletRequest()).getData();

        assertEquals(1, result.size());
        verify(productService, never()).listProductsByIds(anyList());
    }
}
