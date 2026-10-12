package com.lichun.agsell.client;

import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Python AI 服务语义搜索客户端（POST /v1/rec/search，X-Internal-Key 鉴权）
 * <p>
 * 只负责"查询词 → 语义命中商品 id 有序集"，任何异常/超时/低阈值空结果都返回空列表，
 * 由调用方（{@code SearchServiceImpl}）退回 MySQL 全文检索，保证搜索链路不中断。
 */
@Slf4j
@Component
public class AiSearchClient {

    @Value("${ai-service.base-url:http://127.0.0.1:8000}")
    private String aiBaseUrl;

    @Value("${ai-service.internal-key:}")
    private String internalKey;

    /**
     * 语义召回商品 id（相似度降序 + 偏好/销量/好评混合重排）。
     *
     * @param query 搜索框原始词
     * @param topK  期望候选数
     * @return 命中 id 有序列表；服务不可用或被 min-score 闸门全部过滤时返回空列表
     */
    public List<Long> semanticProductIds(String query, int topK) {
        try {
            JSONObject payload = new JSONObject();
            payload.set("query", query);
            payload.set("limit", Math.min(Math.max(topK, 1), 100));
            try (HttpResponse response = cn.hutool.http.HttpRequest.post(aiBaseUrl + "/v1/rec/search")
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Key", internalKey)
                    .body(payload.toString())
                    .timeout(3000)
                    .execute()) {
                if (response.getStatus() != 200 || !JSONUtil.isJson(response.body())) {
                    log.warn("AI 语义搜索返回异常: status={}", response.getStatus());
                    return List.of();
                }
                JSONArray items = JSONUtil.parseObj(response.body()).getJSONArray("items");
                if (items == null || items.isEmpty()) {
                    return List.of();
                }
                List<Long> ids = new ArrayList<>(items.size());
                for (int i = 0; i < items.size(); i++) {
                    Long id = items.getJSONObject(i).getLong("id");
                    if (id != null) {
                        ids.add(id);
                    }
                }
                return ids;
            }
        } catch (Exception e) {
            log.warn("AI 语义搜索服务不可用，退回全文检索：{}", e.getMessage());
            return List.of();
        }
    }
}
