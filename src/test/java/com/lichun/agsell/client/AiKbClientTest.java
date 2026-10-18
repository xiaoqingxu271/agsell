package com.lichun.agsell.client;

import com.lichun.agsell.model.vo.AiFaqPageVO;
import com.lichun.agsell.model.vo.AiKbStatsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ai-service 的 snake_case 响应 → 驼峰 VO 的映射口径。
 *
 * 这层映射是 Java/Python 唯一的字段名接缝，历史上"Python 改了字段名、前端显示 0 条"
 * 这类问题都出在没人测它。解析做成静态方法后，不需要起服务就能钉死口径。
 */
class AiKbClientTest {

    @Test
    @DisplayName("stats 六个字段逐个映射，缺字段落 0 而不是 NPE")
    void parsesStatsWithSnakeCaseKeys() {
        String body = """
                {"faq_count":280,"indexed_count":280,"product_indexed_count":202,
                 "embedding_model":"BAAI/bge-small-zh-v1.5","top_k":5,"product_top_k":8}""";
        AiKbStatsVO vo = AiKbClient.parseStats(body);
        assertEquals(280, vo.getFaqCount());
        assertEquals(280, vo.getIndexedCount());
        assertEquals(202, vo.getProductIndexedCount());
        assertEquals("BAAI/bge-small-zh-v1.5", vo.getEmbeddingModel());
        assertEquals(5, vo.getTopK());
        assertEquals(8, vo.getProductTopK());
        assertTrue(vo.isFaqSynced());

        AiKbStatsVO empty = AiKbClient.parseStats("{}");
        assertEquals(0, empty.getFaqCount());
        assertNull(empty.getEmbeddingModel());
    }

    @Test
    @DisplayName("索引条数与启用条数不一致时状态卡要能报出不同步")
    void detectsIndexLag() {
        AiKbStatsVO vo = AiKbClient.parseStats(
                "{\"faq_count\":281,\"indexed_count\":280,\"product_indexed_count\":200}");
        assertFalse(vo.isFaqSynced());
        vo.setOnSaleProductCount(202);
        assertFalse(vo.isProductIndexSynced());   // 上了 2 件新品还没进索引
        vo.setProductIndexedCount(202);
        assertTrue(vo.isProductIndexSynced());
    }

    @Test
    @DisplayName("FAQ 分页解析：items 缺失或为空都要给出空列表而不是 null")
    void parsesFaqPage() {
        String body = """
                {"total":280,"items":[{"id":12,"question":"运费怎么算","answer":"按重量计费",
                  "category":"shipping","keywords":"运费,包邮","status":1,"updated_at":"2026-10-17 12:00:00"}]}""";
        AiFaqPageVO page = AiKbClient.parseFaqPage(body);
        assertEquals(280, page.getTotal());
        assertEquals(1, page.getItems().size());
        assertEquals(12L, page.getItems().get(0).getId());
        assertEquals("运费怎么算", page.getItems().get(0).getQuestion());
        assertEquals("shipping", page.getItems().get(0).getCategory());
        assertEquals(1, page.getItems().get(0).getStatus());

        assertTrue(AiKbClient.parseFaqPage("{\"total\":0}").getItems().isEmpty());
        assertTrue(AiKbClient.parseFaqPage("{\"total\":0,\"items\":[]}").getItems().isEmpty());
    }

    @Test
    @DisplayName("空 id 列表直接返回，不启动通知线程")
    void notifyIgnoresEmptyIds() {
        AiKbClient client = new AiKbClient();
        assertDoesNotThrow(() -> {
            client.notifyProductIndexAsync(java.util.List.of());
            client.notifyProductIndexAsync((Long) null);
            client.notifyProductIndexAsync(java.util.Arrays.asList(1L, null, 1L));
            // ↑ 有有效 id 时会真的起线程；这里 base-url 指向不存在的端口，
            //   唯一要求是"失败被吞掉只记日志"——不许把异常抛回商品保存流程
        });
    }

    @Test
    @DisplayName("新增结果解析：indexed=false 要一路带到前端，不能只报新增成功")
    void parsesCreateResult() {
        assertEquals(281L, AiKbClient.parseCreate("{\"id\":281,\"indexed\":true}").getId());
        assertTrue(AiKbClient.parseCreate("{\"id\":281,\"indexed\":true}").isIndexed());
        // 向量库挂掉时 Python 仍返回 id（数据已落库），只是 indexed=false
        assertFalse(AiKbClient.parseCreate("{\"id\":281,\"indexed\":false}").isIndexed());
        assertTrue(AiKbClient.parseCreate("{\"id\":281}").isIndexed());   // 缺字段按成功处理，不误报故障
        assertNull(AiKbClient.parseCreate("{}").getId());
    }

    @Test
    @DisplayName("成功判定放宽到 2xx：Python 新增 FAQ 返回 201，只认 200 会把已存库判成失败")
    void treatsAny2xxAsSuccess() {
        assertTrue(AiKbClient.isSuccess(200));
        assertTrue(AiKbClient.isSuccess(201));   // POST /v1/admin/kb/faqs 的真实状态码
        assertFalse(AiKbClient.isSuccess(302));  // 被网关重定向不能当成功
        assertFalse(AiKbClient.isSuccess(401));
        assertFalse(AiKbClient.isSuccess(404));
        assertFalse(AiKbClient.isSuccess(503));
        assertFalse(AiKbClient.isSuccess(199));
    }
}
