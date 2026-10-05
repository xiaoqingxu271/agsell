package com.lichun.agsell.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.model.entity.Review;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Python AI 服务情感分析客户端（Java → Python，X-Internal-Key 鉴权）
 * <p>
 * 契约见 ai-service `app/api/sentiment.py`：
 * POST /v1/sentiment/batch  {items: [{reviewId, content, rating}]} → {results: [{reviewId, label, score, keywords}]}
 */
@Slf4j
@Component
public class SentimentClient {

    @Value("${ai-service.base-url:http://127.0.0.1:8000}")
    private String aiBaseUrl;

    @Value("${ai-service.internal-key:agsell-ai-internal-2026}")
    private String internalKey;

    /** 单批分析上限（与 Python 端 max_length=200 一致） */
    public static final int MAX_BATCH_SIZE = 200;

    /**
     * 批量分析评价情感。失败抛 BusinessException（由调用方决定降级策略）。
     *
     * @return 与入参顺序对应的情感分析结果列表
     */
    public List<AnalyzedReview> analyzeBatch(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }
        JSONArray items = new JSONArray();
        for (Review r : reviews) {
            JSONObject item = new JSONObject();
            item.set("reviewId", r.getId());
            item.set("content", r.getContent() == null ? "" : r.getContent());
            item.set("rating", r.getRating());
            items.add(item);
        }

        JSONObject payload = new JSONObject();
        payload.set("items", items);

        try (cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest
                .post(aiBaseUrl + "/v1/sentiment/batch")
                .header("Content-Type", "application/json")
                .header("X-Internal-Key", internalKey)
                .body(payload.toString())
                .timeout(15000)
                .execute()) {
            if (response.getStatus() != 200) {
                log.error("AI 情感分析服务返回异常: status={}, body={}", response.getStatus(), response.body());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 情感分析服务响应异常，请稍后重试");
            }
            return parseResults(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 情感分析服务失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法连接 AI 情感分析服务，请确认 ai-service 已启动");
        }
    }

    private List<AnalyzedReview> parseResults(String body) {
        JSONObject root = JSONUtil.parseObj(body);
        JSONArray results = root.getJSONArray("results");
        List<AnalyzedReview> list = new ArrayList<>();
        if (results == null) {
            return list;
        }
        for (Object o : results) {
            JSONObject r = (JSONObject) o;
            AnalyzedReview ar = new AnalyzedReview();
            ar.setReviewId(r.getLong("reviewId"));
            ar.setLabel(r.getInt("label"));
            ar.setScore(r.getBigDecimal("score"));
            JSONArray kws = r.getJSONArray("keywords");
            ar.setKeywords(kws == null ? List.of() : kws.toList(String.class));
            list.add(ar);
        }
        return list;
    }

    /** 单条分析结果（Python 返回的 label/score/keywords） */
    @lombok.Data
    public static class AnalyzedReview {
        private Long reviewId;
        private Integer label;
        private BigDecimal score;
        private List<String> keywords;
    }
}
