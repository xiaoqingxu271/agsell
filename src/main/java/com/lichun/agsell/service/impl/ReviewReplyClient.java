package com.lichun.agsell.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.model.entity.Review;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Python AI 服务评价回复客户端（Java → Python，X-Internal-Key 鉴权）
 * <p>
 * 契约见 ai-service `app/api/review_reply.py`：
 * POST /v1/review/reply  {reviewId, content, rating, sentimentLabel, productName}
 * → {reviewId, reply, source: "llm" | "fallback"}
 */
@Slf4j
@Component
public class ReviewReplyClient {

    @Value("${ai-service.base-url:http://127.0.0.1:8000}")
    private String aiBaseUrl;

    @Value("${ai-service.internal-key:agsell-ai-internal-2026}")
    private String internalKey;

    /**
     * 生成单条评价的 AI 回复草稿。失败抛 BusinessException（调用方决定是否降级）。
     */
    public AiReplyDraft generateReply(Review review, String productName) {
        JSONObject payload = new JSONObject();
        payload.set("reviewId", review.getId());
        payload.set("content", review.getContent() == null ? "" : review.getContent());
        payload.set("rating", review.getRating());
        payload.set("sentimentLabel", review.getSentimentLabel());
        payload.set("productName", productName == null ? "" : productName);

        try (cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest
                .post(aiBaseUrl + "/v1/review/reply")
                .header("Content-Type", "application/json")
                .header("X-Internal-Key", internalKey)
                .body(payload.toString())
                .timeout(30000)  // LLM 生成比批量情感分析慢，对齐 Python 侧 LLM_TIMEOUT
                .execute()) {
            if (response.getStatus() != 200) {
                log.error("AI 评价回复服务返回异常: status={}, body={}", response.getStatus(), response.body());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 评价回复服务响应异常，请稍后重试");
            }
            return parseDraft(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 评价回复服务失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法连接 AI 评价回复服务，请确认 ai-service 已启动");
        }
    }

    private AiReplyDraft parseDraft(String body) {
        JSONObject root = JSONUtil.parseObj(body);
        AiReplyDraft draft = new AiReplyDraft();
        draft.setReply(root.getStr("reply"));
        draft.setSource(root.getStr("source"));
        if (draft.getReply() == null || draft.getReply().isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 评价回复服务返回内容为空");
        }
        return draft;
    }

    /** 单条回复草稿（Python 返回的 reply + source） */
    @lombok.Data
    public static class AiReplyDraft {
        private String reply;
        /** llm = Agnes 生成；fallback = 规则模板兜底（Agnes 不可用时） */
        private String source;
    }
}
