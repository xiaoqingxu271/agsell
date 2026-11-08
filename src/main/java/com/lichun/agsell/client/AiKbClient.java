package com.lichun.agsell.client;

import cn.hutool.core.io.resource.BytesResource;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.model.dto.AiFaqRequest;
import com.lichun.agsell.model.vo.AiConfigItemVO;
import com.lichun.agsell.model.vo.AiDocListVO;
import com.lichun.agsell.model.vo.AiDocUploadVO;
import com.lichun.agsell.model.vo.AiFaqCreateVO;
import com.lichun.agsell.model.vo.AiFaqPageVO;
import com.lichun.agsell.model.vo.AiFaqVO;
import com.lichun.agsell.model.vo.AiKbStatsVO;
import com.lichun.agsell.model.vo.AiOpsSummaryVO;
import com.lichun.agsell.model.vo.KbHitTopVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 服务知识库管理客户端（管理端「知识库维护」与商品索引增量同步的唯一出口）。
 *
 * 为什么要经 Java 代理而不是前端直连 Python：X-Internal-Key 只能待在服务端，
 * 8000 端口也不该对浏览器开放；Java 侧再套一层管理员 JWT 与角色校验，
 * 前端拿到的是统一的 BaseResponse 口径。
 *
 * Python 侧字段是 snake_case（faq_count/indexed_count），这里逐字段映射成驼峰 VO，
 * 而不是把下划线名透给前端——前端类型与 Python 内部结构解耦，Python 改字段名时
 * 编译期就能在这里发现（映射方法做成静态包级方法，单测无需起服务即可校验口径）。
 */
@Slf4j
@Component
public class AiKbClient {

    /** FAQ 增删改查：读的是 MySQL + 单条向量化，正常都在几百毫秒内 */
    private static final int CRUD_TIMEOUT_MS = 8000;

    /** 全量重建要对几百条做 bge 向量化，本地推理慢但仍在秒级；给足额度避免管理员看到"失败" */
    private static final int REBUILD_TIMEOUT_MS = 120000;

    /** 商品变更后的增量通知：fire-and-forget，超时就放弃本次（全量重建按钮是兜底） */
    private static final int NOTIFY_TIMEOUT_MS = 5000;

    @Value("${ai-service.base-url:http://127.0.0.1:8000}")
    private String aiBaseUrl;

    @Value("${ai-service.internal-key:}")
    private String internalKey;

    /** 知识库与索引状态 */
    public AiKbStatsVO stats() {
        return parseStats(get("/v1/admin/kb/stats", CRUD_TIMEOUT_MS));
    }

    /** 近 N 日 AI 运营汇总（对话量/拒答率/工具分布/按日趋势） */
    public AiOpsSummaryVO ops(int days) {
        return parseOps(get("/v1/admin/kb/ops?days=" + Math.max(1, Math.min(days, 30)), CRUD_TIMEOUT_MS));
    }

    /** FAQ 分页列表；category 为空表示全部分类 */
    public AiFaqPageVO listFaqs(String category, int pageNum, int pageSize) {
        StringBuilder path = new StringBuilder("/v1/admin/kb/faqs?page=")
                .append(Math.max(pageNum, 1)).append("&size=").append(Math.min(Math.max(pageSize, 1), 100));
        if (category != null && !category.isBlank()) {
            path.append("&category=").append(category.trim());
        }
        return parseFaqPage(get(path.toString(), CRUD_TIMEOUT_MS));
    }

    /** 新增 FAQ：返回新条目 id 与索引同步结果（indexed=false 时前端必须提示重建） */
    public AiFaqCreateVO createFaq(AiFaqRequest request) {
        return parseCreate(post("/v1/admin/kb/faqs", body(request, false), CRUD_TIMEOUT_MS));
    }

    /**
     * 更新 FAQ。只送非 null 字段：Python 侧是 PATCH 语义（exclude_none），
     * 把没填的字段当 null 送过去会清空关键词与分类。
     *
     * 返回的是"索引同步是否成功"，不是"这条在不在索引里"：停用一条时除名成功也返回 true
     * （实测 PUT status=0 → indexed=true 且索引条数 -1），前端据此只在 false 时报警。
     */
    public boolean updateFaq(long id, AiFaqRequest request) {
        JSONObject json = JSONUtil.parseObj(put("/v1/admin/kb/faqs/" + id, body(request, true), CRUD_TIMEOUT_MS));
        // Python 用 indexed=false 表示"已存库但索引同步失败"——数据没坏，但要让运营知道可能要重建
        return !Boolean.FALSE.equals(json.getBool("indexed", true));
    }

    /** 删除 FAQ（Python 侧同时从向量索引除名） */
    public void deleteFaq(long id) {
        delete("/v1/admin/kb/faqs/" + id, CRUD_TIMEOUT_MS);
    }

    /** FAQ 索引全量重建，返回写入条数 */
    public long rebuildFaqIndex() {
        return JSONUtil.parseObj(post("/v1/admin/kb/index", "{}", REBUILD_TIMEOUT_MS)).getLong("indexed", 0L);
    }

    /**
     * 上传文档切块入库（txt/md/docx/pdf → 抽文本 → 切块 → ai_faq → 向量索引）。
     *
     * 走 multipart 转发而不是 base64 塞 JSON：Python 侧用 FastAPI UploadFile 收流，
     * 文件体不进 JSON 序列化路径；抽取+切块+批量向量化在服务端是秒级操作，
     * 超时口径与全量重建一致（REBUILD_TIMEOUT_MS）。
     */
    public AiDocUploadVO uploadDocument(String fileName, byte[] data, String title) {
        cn.hutool.http.HttpRequest request = cn.hutool.http.HttpRequest.post(url("/v1/admin/kb/documents"))
                .form("file", new BytesResource(data, fileName));
        if (title != null && !title.isBlank()) {
            request.form("title", title.trim());
        }
        return parseDocUpload(executeMultipart(request, "/v1/admin/kb/documents", REBUILD_TIMEOUT_MS));
    }

    /** 已入库文档清单（按来源聚合块数） */
    public AiDocListVO listDocuments() {
        return parseDocList(get("/v1/admin/kb/documents", CRUD_TIMEOUT_MS));
    }

    /** 按文档名整批删除（DB 行 + 向量索引），返回删除块数 */
    public long deleteDocument(String name) {
        String encoded = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8);
        return JSONUtil.parseObj(executeWithDetail(
                cn.hutool.http.HttpRequest.delete(url("/v1/admin/kb/documents?name=" + encoded)),
                "/v1/admin/kb/documents", CRUD_TIMEOUT_MS))
                .getLong("deleted", 0L);
    }

    /** AI 运行参数快照（当前值/默认值/是否覆盖/边界） */
    public List<AiConfigItemVO> getConfig() {
        return parseConfig(get("/v1/admin/kb/config", CRUD_TIMEOUT_MS));
    }

    /** 批量更新运行参数（Python 侧 PUT 后立即生效），返回更新后的全量快照 */
    public List<AiConfigItemVO> updateConfig(Map<String, String> values) {
        // Python 侧契约是 {"values": {key: value}}，平铺发过去会被 pydantic 静默忽略、
        // 返回 200 + 未变化的旧快照——调参页"保存成功"却什么都没改，比 500 更糟
        JSONObject payload = new JSONObject();
        values.forEach(payload::set);
        JSONObject wrapper = new JSONObject();
        wrapper.set("values", payload);
        return parseConfig(put("/v1/admin/kb/config", wrapper.toString(), CRUD_TIMEOUT_MS));
    }

    /** 商品召回索引全量重建（上一步失灵或历史脏数据时的兜底修复） */
    public long rebuildProductIndex() {
        return JSONUtil.parseObj(post("/v1/admin/kb/products/index", "{}", REBUILD_TIMEOUT_MS))
                .getLong("indexed", 0L);
    }

    /**
     * 商品变更后异步通知 Python 更新召回索引（在售的 upsert、下架/删除的除名）。
     *
     * 三条设计都关系到"管理员保存商品这件事会不会被拖垮或误导"：
     * ① 事务未提交前不能通知——Python 会读到旧数据甚至读不到，索引照旧落后，
     *    所以有事务时挂到 afterCommit，没事务时直接派发；
     * ② 虚拟线程后台跑，不占请求线程，管理员不需要等向量化；
     * ③ 失败只记日志不抛错：索引是派生数据，向量化再挂也不该让"保存商品"失败，
     *    真不同步了还有管理页的全量重建按钮兜底。
     */
    public void notifyProductIndexAsync(List<Long> productIds) {
        List<Long> ids = productIds == null ? List.of()
                : productIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(ids);
                }
            });
        } else {
            dispatch(ids);
        }
    }

    /** 商品变更后的单条通知便捷入口（新增/编辑/上下架/删除都用它） */
    public void notifyProductIndexAsync(Long productId) {
        if (productId != null) {
            notifyProductIndexAsync(List.of(productId));
        }
    }

    private void dispatch(List<Long> ids) {
        Thread.ofVirtual().start(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.set("productIds", ids);
                String body = post("/v1/admin/kb/products/upsert", payload.toString(), NOTIFY_TIMEOUT_MS);
                JSONObject json = JSONUtil.parseObj(body);
                log.info("商品召回索引增量更新完成：ids={}, indexed={}, deindexed={}",
                        ids, json.getLong("indexed", 0L), json.getLong("deindexed", 0L));
            } catch (Exception e) {
                // 不抛：管理员的"保存商品"已经成功了，这里失败只影响 AI 召回，日志留痕即可
                log.warn("商品召回索引增量更新失败（可在知识库管理页全量重建）：ids={}, 原因={}",
                        ids, e.getMessage());
            }
        });
    }

    // ---------- 解析（与 HTTP 分离，便于无服务单测） ----------

    static AiDocUploadVO parseDocUpload(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        AiDocUploadVO vo = new AiDocUploadVO();
        vo.setName(obj.getStr("name"));
        vo.setChunks(obj.getLong("chunks", 0L));
        vo.setReplaced(obj.getLong("replaced", 0L));
        vo.setIndexed(!Boolean.FALSE.equals(obj.getBool("indexed", true)));
        return vo;
    }

    static AiDocListVO parseDocList(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        AiDocListVO vo = new AiDocListVO();
        List<AiDocListVO.Item> items = new ArrayList<>();
        JSONArray arr = obj.getJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JSONObject row = arr.getJSONObject(i);
                AiDocListVO.Item item = new AiDocListVO.Item();
                item.setName(row.getStr("name"));
                item.setChunks(row.getLong("chunks", 0L));
                items.add(item);
            }
        }
        vo.setItems(items);
        return vo;
    }

    static List<AiConfigItemVO> parseConfig(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        List<AiConfigItemVO> items = new ArrayList<>();
        JSONArray arr = obj.getJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JSONObject row = arr.getJSONObject(i);
                AiConfigItemVO vo = new AiConfigItemVO();
                vo.setKey(row.getStr("key"));
                vo.setDescription(row.getStr("description"));
                vo.setType(row.getStr("type"));
                vo.setValue(typedValue(row, "value"));
                vo.setDefaultValue(typedValue(row, "default"));
                vo.setOverridden(row.getBool("overridden", false));
                vo.setMin(typedValue(row, "min"));
                vo.setMax(typedValue(row, "max"));
                items.add(vo);
            }
        }
        return items;
    }

    /**
     * 按 Python 声明的 type 把混合值归一成强类型（null/JSONNull → null）。
     * 不归一直接 row.get(Object) 透传的话：hutool 把 0.5 解析成 BigDecimal、
     * null 解析成 JSONNull，Jackson 再序列化分别输出字符串与空对象 {}——
     * 前端 el-input-number 拿到字符串直接渲染异常，布尔默认值恒显示 false。
     */
    private static Object typedValue(JSONObject row, String field) {
        Object raw = row.get(field);
        if (raw == null || raw instanceof cn.hutool.json.JSONNull) {
            return null;
        }
        String type = row.getStr("type", "");
        String text = String.valueOf(raw);
        try {
            switch (type) {
                case "bool":
                    return Boolean.parseBoolean(text);
                case "int":
                    return Long.valueOf(text);
                case "float":
                    return Double.valueOf(text);
                default:
                    return text;
            }
        } catch (NumberFormatException e) {
            // 理论不可达（Python 端已归一），兜底原样透传避免整条配置丢失
            return text;
        }
    }

    static AiOpsSummaryVO parseOps(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        AiOpsSummaryVO vo = new AiOpsSummaryVO();
        vo.setDays(obj.getInt("days", 7));
        vo.setTotalTurns(obj.getLong("totalTurns", 0L));
        vo.setRefusedTurns(obj.getLong("refusedTurns", 0L));
        vo.setRefusalRate(obj.getDouble("refusalRate", 0.0));
        Map<String, Long> byTool = new java.util.LinkedHashMap<>();
        JSONObject toolObj = obj.getJSONObject("byTool");
        if (toolObj != null) {
            for (String key : toolObj.keySet()) {
                byTool.put(key, toolObj.getLong(key, 0L));
            }
        }
        vo.setByTool(byTool);
        List<AiOpsSummaryVO.DailyCount> daily = new ArrayList<>();
        JSONArray dayArr = obj.getJSONArray("daily");
        if (dayArr != null) {
            for (int i = 0; i < dayArr.size(); i++) {
                JSONObject row = dayArr.getJSONObject(i);
                AiOpsSummaryVO.DailyCount d = new AiOpsSummaryVO.DailyCount();
                d.setDate(row.getStr("date"));
                d.setCount(row.getLong("count", 0L));
                daily.add(d);
            }
        }
        vo.setDaily(daily);
        return vo;
    }

    static AiKbStatsVO parseStats(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        AiKbStatsVO vo = new AiKbStatsVO();
        vo.setFaqCount(obj.getLong("faq_count", 0L));
        vo.setIndexedCount(obj.getLong("indexed_count", 0L));
        vo.setProductIndexedCount(obj.getLong("product_indexed_count", 0L));
        vo.setEmbeddingModel(obj.getStr("embedding_model"));
        vo.setTopK(obj.getInt("top_k"));
        vo.setProductTopK(obj.getInt("product_top_k"));
        // 近 7 日引用 Top：无日志数据时 Python 返回空数组，保持 null 以便前端区分"没开"与"没数据"
        JSONArray hitArr = obj.getJSONArray("recent_hit_top");
        if (hitArr != null && !hitArr.isEmpty()) {
            List<KbHitTopVO> hitTop = new ArrayList<>();
            for (int i = 0; i < hitArr.size(); i++) {
                JSONObject row = hitArr.getJSONObject(i);
                KbHitTopVO item = new KbHitTopVO();
                item.setFaqId(row.getLong("faqId", 0L));
                item.setQuestion(row.getStr("question"));
                item.setHits(row.getLong("hits", 0L));
                hitTop.add(item);
            }
            vo.setRecentHitTop(hitTop);
        }
        return vo;
    }

    static AiFaqCreateVO parseCreate(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        AiFaqCreateVO vo = new AiFaqCreateVO();
        vo.setId(obj.getLong("id"));
        vo.setIndexed(!Boolean.FALSE.equals(obj.getBool("indexed", true)));
        return vo;
    }

    static AiFaqPageVO parseFaqPage(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        AiFaqPageVO page = new AiFaqPageVO();
        page.setTotal(obj.getLong("total", 0L));
        List<AiFaqVO> items = new ArrayList<>();
        JSONArray arr = obj.getJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JSONObject row = arr.getJSONObject(i);
                AiFaqVO vo = new AiFaqVO();
                vo.setId(row.getLong("id"));
                vo.setQuestion(row.getStr("question"));
                vo.setAnswer(row.getStr("answer"));
                vo.setCategory(row.getStr("category"));
                vo.setKeywords(row.getStr("keywords"));
                vo.setStatus(row.getInt("status"));
                vo.setUpdatedAt(row.getStr("updated_at"));
                items.add(vo);
            }
        }
        page.setItems(items);
        return page;
    }

    private static String body(AiFaqRequest request, boolean partial) {
        JSONObject payload = new JSONObject();
        if (request.getQuestion() != null && (!partial || !request.getQuestion().isBlank())) {
            payload.set("question", request.getQuestion().trim());
        }
        if (request.getAnswer() != null && (!partial || !request.getAnswer().isBlank())) {
            payload.set("answer", request.getAnswer().trim());
        }
        if (request.getCategory() != null) {
            payload.set("category", request.getCategory().trim());
        }
        if (request.getKeywords() != null) {
            payload.set("keywords", request.getKeywords().trim());
        }
        if (request.getStatus() != null) {
            payload.set("status", request.getStatus());
        }
        return payload.toString();
    }

    // ---------- HTTP 薄封装 ----------

    private String get(String path, int timeoutMs) {
        return execute(cn.hutool.http.HttpRequest.get(url(path)), path, timeoutMs);
    }

    private String post(String path, String payload, int timeoutMs) {
        return execute(cn.hutool.http.HttpRequest.post(url(path)).body(payload), path, timeoutMs);
    }

    private String put(String path, String payload, int timeoutMs) {
        return execute(cn.hutool.http.HttpRequest.put(url(path)).body(payload), path, timeoutMs);
    }

    private String delete(String path, int timeoutMs) {
        return execute(cn.hutool.http.HttpRequest.delete(url(path)), path, timeoutMs);
    }

    private String execute(cn.hutool.http.HttpRequest request, String path, int timeoutMs) {
        String fullUrl = url(path);
        try (cn.hutool.http.HttpResponse response = request
                .header("Content-Type", "application/json")
                .header("X-Internal-Key", internalKey)
                .timeout(timeoutMs)
                .execute()) {
            if (!isSuccess(response.getStatus())) {
                // 只把状态码写进日志；给用户的文案固定，不带 Python 的 detail（可能含路径/内部信息）
                log.error("AI 知识库接口返回异常: path={}, status={}", path, response.getStatus());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, friendly(path, response.getStatus()));
            }
            return response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 知识库接口失败: path={}", path, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "无法连接 AI 服务，请确认 ai-service 已启动后重试");
        }
    }

    /**
     * multipart 专用执行：不能带 Content-Type 头（boundary 由 HTTP 库自己生成）。
     * 400 透传 Python 的 detail——"仅支持 txt/md/docx/pdf"这类校验文案是给管理员
     * 看的操作指引，吞掉反而让人反复试错；401/503 等仍走固定文案。
     */
    private String executeMultipart(cn.hutool.http.HttpRequest request, String path, int timeoutMs) {
        try (cn.hutool.http.HttpResponse response = request
                .header("X-Internal-Key", internalKey)
                .timeout(timeoutMs)
                .execute()) {
            if (!isSuccess(response.getStatus())) {
                log.error("AI 知识库文档上传返回异常: path={}, status={}", path, response.getStatus());
                if (response.getStatus() == 400) {
                    // detail 解析单独兜底：400 也可能来自反向代理（HTML 错误页），
                    // 解析失败被外层 catch 吞掉会误报"无法连接 AI 服务"，方向完全反了
                    String detail = "";
                    try {
                        detail = JSONUtil.parseObj(response.body()).getStr("detail", "");
                    } catch (Exception ignore) {
                        // 非 JSON 体，走下方固定文案
                    }
                    if (!detail.isBlank()) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, detail);
                    }
                }
                throw new BusinessException(ErrorCode.OPERATION_ERROR, friendly(path, response.getStatus()));
            }
            return response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 知识库文档上传失败: path={}", path, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "无法连接 AI 服务，请确认 ai-service 已启动后重试");
        }
    }

    /**
     * 带业务 detail 透传的 DELETE（文档删除等"资源可能不存在"的调用）：
     * 通用 execute() 把 404 一律翻译成"AI 服务版本过旧"，但两管理员并发删同一文档时
     * Python 返回的 404"文档不存在"是正常业务结果，文案会误导运营。
     */
    private String executeWithDetail(cn.hutool.http.HttpRequest request, String path, int timeoutMs) {
        try (cn.hutool.http.HttpResponse response = request
                .header("X-Internal-Key", internalKey)
                .timeout(timeoutMs)
                .execute()) {
            if (!isSuccess(response.getStatus())) {
                log.error("AI 知识库接口返回异常: path={}, status={}", path, response.getStatus());
                String detail = "";
                try {
                    detail = JSONUtil.parseObj(response.body()).getStr("detail", "");
                } catch (Exception ignore) {
                    // 非 JSON 体
                }
                if (!detail.isBlank()) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, detail);
                }
                throw new BusinessException(ErrorCode.OPERATION_ERROR, friendly(path, response.getStatus()));
            }
            return response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 知识库接口失败: path={}", path, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "无法连接 AI 服务，请确认 ai-service 已启动后重试");
        }
    }

    /**
     * 2xx 一律算成功，不能只认 200。
     *
     * 端到端实测踩到的坑：Python 的新增 FAQ 是 POST /v1/admin/kb/faqs → 201 Created，
     * 只认 200 时这条 FAQ **已经写库并入了索引**，但 Java 抛"AI 服务处理失败"给管理员，
     * 运营再点一次保存就写出重复条目——报错方向完全反了（成功被判成失败，且不可回滚）。
     * 其余客户端（chat/推荐/情感/评价回复）打的都是 200 接口，只有这里需要放宽。
     */
    static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    /**
     * 状态码 → 用户可读文案。401/503 都指向配置问题而不是"操作失败"，
     * 运营看到"服务未就绪"才知道去找技术，而不是反复点保存。
     */
    private static String friendly(String path, int status) {
        if (status == 401 || status == 403) {
            return "AI 服务鉴权失败：内部密钥不一致，请检查两侧配置";
        }
        if (status == 503) {
            return "AI 服务尚未就绪（内部密钥未配置或索引暂时不可用）";
        }
        if (status == 404) {
            return "AI 服务版本过旧，缺少该知识库接口";
        }
        return "AI 服务处理失败，请稍后重试";
    }

    private String url(String path) {
        return aiBaseUrl.replaceAll("/+$", "") + path;
    }
}
