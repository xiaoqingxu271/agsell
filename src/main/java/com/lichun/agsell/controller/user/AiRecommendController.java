package com.lichun.agsell.controller.user;

import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.vo.ProductListItemVO;
import com.lichun.agsell.service.ProductService;
import com.lichun.agsell.utils.ResultUtils;
import com.lichun.agsell.utils.UserContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户端-AI 个性化推荐接口
 * <p>
 * 首页"为你推荐"楼层：调 Python /v1/rec/feed 取混合排序结果（类目偏好+销量+好评率），
 * 游客、AI 服务不可用或返回异常时静默回退热销榜——推荐位永不空窗。
 */
@Slf4j
@Tag(name = "用户端-AI 个性化推荐", description = "首页猜你喜欢（AI 混合排序，热销兜底）")
@RestController
@RequestMapping("/product/recommend")
@RequiredArgsConstructor
public class AiRecommendController {

    @Value("${ai-service.base-url:http://127.0.0.1:8000}")
    private String aiBaseUrl;

    @Value("${ai-service.internal-key:}")
    private String internalKey;

    private final ProductService productService;
    private final UserContextResolver userContextResolver;

    @Operation(summary = "首页个性化推荐 feed")
    @GetMapping("/feed")
    public BaseResponse<List<ProductListItemVO>> feed(@RequestParam(defaultValue = "10") int limit,
                                                      HttpServletRequest httpRequest) {
        int n = Math.min(Math.max(limit, 1), 50);
        Long userId = userContextResolver.resolveUserId(httpRequest);

        List<Long> orderedIds = null;
        Map<Long, JSONObject> signalsById = null;
        try {
            JSONObject payload = new JSONObject();
            if (userId != null) {
                payload.set("userId", userId);
            }
            payload.set("limit", n);
            try (HttpResponse response = cn.hutool.http.HttpRequest.post(aiBaseUrl + "/v1/rec/feed")
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Key", internalKey)
                    .body(payload.toString())
                    .timeout(5000)
                    .execute()) {
                if (response.getStatus() == 200 && JSONUtil.isJson(response.body())) {
                    JSONArray items = JSONUtil.parseObj(response.body()).getJSONArray("items");
                    if (items != null && !items.isEmpty()) {
                        orderedIds = new ArrayList<>(items.size());
                        signalsById = new HashMap<>(items.size());
                        for (int i = 0; i < items.size(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            Long id = item.getLong("id");
                            if (id != null) {
                                orderedIds.add(id);
                                JSONObject signals = item.getJSONObject("signals");
                                if (signals != null) {
                                    signalsById.put(id, signals);
                                }
                            }
                        }
                    }
                } else {
                    log.warn("AI 推荐 feed 返回异常: status={}", response.getStatus());
                }
            }
        } catch (Exception e) {
            log.warn("AI 推荐服务不可用，回退热销榜：{}", e.getMessage());
        }

        if (orderedIds == null || orderedIds.isEmpty()) {
            return ResultUtils.success(productService.listHotProducts(n));
        }
        List<ProductListItemVO> products = productService.listProductsByIds(orderedIds);
        if (products.isEmpty()) {
            return ResultUtils.success(productService.listHotProducts(n));
        }
        // 附可解释理由：取 AI 原始信号（0~1）最强者作为"为什么推荐给你"标签
        Map<Long, JSONObject> sigRef = signalsById;
        products.forEach(vo -> vo.setRecommendReason(buildReason(sigRef.get(vo.getId()), vo.getCategoryName())));
        return ResultUtils.success(products);
    }

    /** 原始信号（0~1）择优 → 人类可读理由标签；信号全为 0（如热销兜底）返回 null 不展示 */
    static String buildReason(JSONObject signals, String categoryName) {
        if (signals == null) {
            return null;
        }
        double sem = signals.getDouble("sem", 0.0);
        double pref = signals.getDouble("pref", 0.0);
        double sales = signals.getDouble("sales", 0.0);
        double rate = signals.getDouble("rate", 0.0);
        double fresh = signals.getDouble("fresh", 0.0);
        double browse = signals.getDouble("browse", 0.0);
        double max = Math.max(Math.max(Math.max(sem, pref), Math.max(sales, rate)), Math.max(fresh, browse));
        if (max <= 0.0) {
            return null;
        }
        // 并列时优先级：搜索相关 > 最近浏览 > 长期偏好 > 时令 > 口碑 > 销量
        if (sem == max) {
            return "与你的搜索高度相关";
        }
        if (browse == max) {
            return (categoryName != null && !categoryName.isBlank())
                    ? "你最近看过「" + categoryName + "」" : "符合你最近的浏览";
        }
        if (pref == max) {
            return (categoryName != null && !categoryName.isBlank())
                    ? "你常买「" + categoryName + "」" : "符合你的偏好";
        }
        if (fresh == max) {
            return "日期新鲜";
        }
        if (rate == max) {
            return "好评率高";
        }
        return "热销爆款";
    }
}
