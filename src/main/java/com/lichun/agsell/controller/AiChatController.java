package com.lichun.agsell.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.model.dto.AiChatRequest;
import com.lichun.agsell.model.vo.AiChatReplyVO;
import com.lichun.agsell.utils.JwtUtils;
import com.lichun.agsell.utils.ResultUtils;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端-AI 智能客服接口
 * <p>
 * 调用链：小程序 → Java（校验/解析 JWT 取 userId）→ Python /v1/chat → 返回回复。
 * 游客（无 token）也可咨询 FAQ；业务查询（订单/物流/售后）需登录，
 * 未登录时由 Python 侧返回引导话术。
 */
@Slf4j
@Tag(name = "AI 智能客服", description = "用户端 AI 客服对话（转发 Python AI 服务）")
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    @Value("${ai-service.base-url:http://127.0.0.1:8000}")
    private String aiBaseUrl;

    @Value("${ai-service.internal-key:}")
    private String internalKey;

    private final JwtUtils jwtUtils;

    @Operation(summary = "AI 客服对话")
    @PostMapping
    public BaseResponse<AiChatReplyVO> chat(@RequestBody AiChatRequest request, HttpServletRequest httpRequest) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }
        if (request.getMessage().length() > 2000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息过长（最多 2000 字）");
        }

        Long userId = resolveUserId(httpRequest);
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "s_" + System.currentTimeMillis();
        }

        JSONObject payload = new JSONObject();
        payload.set("sessionId", sessionId);
        payload.set("message", request.getMessage());
        if (userId != null) {
            payload.set("userId", userId);
        }

        try (HttpResponse response = HttpRequest.post(aiBaseUrl + "/v1/chat")
                .header("Content-Type", "application/json")
                .header("X-Internal-Key", internalKey)
                .body(payload.toString())
                .timeout(60000)  // Agnes 生成 + 业务查询可能较慢，读超时放宽到 60s
                .execute()) {
            if (response.getStatus() != 200) {
                log.error("AI 服务返回异常状态: status={}, body={}", response.getStatus(), response.body());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 服务响应异常，请稍后重试");
            }
            String body = response.body();
            if (!JSONUtil.isJson(body)) {
                log.error("AI 服务返回非 JSON: {}", body);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 服务响应异常，请稍后重试");
            }
            JSONObject json = JSONUtil.parseObj(body);
            AiChatReplyVO vo = new AiChatReplyVO();
            vo.setSessionId(json.getStr("sessionId", sessionId));
            vo.setReply((json.getStr("reply", "")).trim());
            // 下一轮建议问题：hutool 对 String 泛型 bean 列表支持不稳，手动遍历
            cn.hutool.json.JSONArray arr = json.getJSONArray("suggestions");
            if (arr != null) {
                java.util.List<String> suggestions = new java.util.ArrayList<>(arr.size());
                for (Object item : arr) {
                    if (item != null) {
                        suggestions.add(String.valueOf(item));
                    }
                }
                vo.setSuggestions(suggestions);
            }
            return ResultUtils.success(vo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 服务失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 解析 JWT 中的 userId（游客返回 null，业务查询由 Python 侧引导登录）
     */
    private Long resolveUserId(HttpServletRequest request) {
        try {
            String token = jwtUtils.getTokenFromRequest(request);
            if (token == null) {
                return null;
            }
            Claims claims = jwtUtils.parseToken(token);
            String type = claims.get("type", String.class);
            if (!"user".equals(type)) {
                return null;
            }
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            // token 无效视为游客，不阻断 FAQ 咨询
            return null;
        }
    }
}
