package com.lichun.agsell.controller.user;

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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户端-AI 智能客服接口
 * <p>
 * 调用链：小程序 → Java（校验/解析 JWT 取 userId）→ Python /v1/chat → 返回回复。
 * 游客（无 token）也可咨询 FAQ；业务查询（订单/物流/售后）需登录，
 * 未登录时由 Python 侧返回引导话术。
 * <p>
 * 流式版：POST /ai/chat/stream（SSE）——Java 用虚拟线程逐行读取 Python 的 SSE 事件
 * （status/token/done/error）并经 SseEmitter 透传给小程序，实现回复逐字渲染；
 * 白名单复用 /api/ai/chat 前缀匹配，游客可咨询，登录后业务查询带 userId。
 */
@Slf4j
@Tag(name = "AI 智能客服", description = "用户端 AI 客服对话（转发 Python AI 服务，支持 SSE 流式）")
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    /** SSE 流整体超时：需覆盖最坏情况（意图识别重试 + 业务查询 + 生成重试） */
    private static final long SSE_TIMEOUT_MS = 120_000L;

    /** HttpClient 不可变可复用；SSE 响应头超时放宽到 90s（业务查询链路较慢） */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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

        // cn.hutool 的 HttpRequest 与 java.net.http.HttpRequest 简名冲突，这里用全限定名
        try (cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest.post(aiBaseUrl + "/v1/chat")
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

    @Operation(summary = "AI 客服对话（SSE 流式）",
            description = "事件流：status（业务阶段）→ token（逐字）→ done（建议问题）/ error")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AiChatRequest request, HttpServletRequest httpRequest) {
        // 入参校验与同步接口一致
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

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 虚拟线程：阻塞读 Python SSE 并逐事件转发，不占用平台线程
        Thread.ofVirtual().start(() -> forwardStream(payload, emitter));
        return emitter;
    }

    /**
     * 转发 Python /v1/chat/stream 的 SSE 事件流到 SseEmitter。
     * 上游异常以 error 事件下发后正常收尾（流已开始，状态码无法再改）。
     */
    private void forwardStream(JSONObject payload, SseEmitter emitter) {
        try {
            HttpRequest aiRequest = HttpRequest.newBuilder(URI.create(aiBaseUrl + "/v1/chat/stream"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("X-Internal-Key", internalKey)
                    .timeout(Duration.ofSeconds(90))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<java.io.InputStream> response =
                    HTTP_CLIENT.send(aiRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                sendErrorEvent(emitter, "AI 服务响应异常（HTTP " + response.statusCode() + "）");
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String event = "message";
                List<String> dataLines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        event = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        dataLines.add(line.substring("data:".length()).stripLeading());
                    } else if (line.isEmpty() && !dataLines.isEmpty()) {
                        // 空行 = 一条事件结束（Python 侧 data 均为单行 JSON）；
                        // 以 UTF-8 字节发送，避免 StringHttpMessageConverter 默认 ISO-8859-1 中文乱码
                        emitter.send(SseEmitter.event().name(event).data(toUtf8Bytes(String.join("\n", dataLines))));
                        event = "message";
                        dataLines.clear();
                    }
                }
            }
            emitter.complete();
        } catch (Exception e) {
            log.error("AI 流式转发失败", e);
            sendErrorEvent(emitter, "AI 服务暂时不可用，请稍后重试");
        }
    }

    /** 下发 error 事件并收尾；客户端已断开时静默忽略 */
    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            JSONObject err = new JSONObject();
            err.set("message", message);
            emitter.send(SseEmitter.event().name("error").data(toUtf8Bytes(err.toString())));
            emitter.complete();
        } catch (Exception ignored) {
            // 连接已断开，无需处理
        }
    }

    private static byte[] toUtf8Bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
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
