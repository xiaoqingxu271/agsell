package com.lichun.agsell.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.client.AiKbClient;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.utils.ResultUtils;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.annotation.OperationLog;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.model.dto.AiFaqRequest;
import com.lichun.agsell.model.entity.Product;
import com.lichun.agsell.model.vo.AiFaqCreateVO;
import com.lichun.agsell.model.vo.AiFaqPageVO;
import com.lichun.agsell.model.vo.AiKbStatsVO;
import com.lichun.agsell.model.vo.AiOpsSummaryVO;
import com.lichun.agsell.utils.ThrowUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-AI 知识库维护（RAG 知识条数、FAQ 增删改查、两个索引的重建）。
 *
 * 数据源与索引都在 ai-service（Python）侧，Java 只做鉴权代理与参数校验，
 * 不再持有一份 FAQ 表——两处都能写同一张表迟早会分叉。
 * 角色：SUPER_ADMIN 与 ADMIN 可读写，OPERATOR 由 JwtAuthInterceptor 按前缀拒绝
 * （运营专员只给商品/订单/分类/统计）。
 */
@Slf4j
@Tag(name = "管理端-AI 知识库", description = "RAG 知识库维护与向量索引重建")
@RestController
@RequestMapping("/admin/ai/kb")
@RequiredArgsConstructor
public class AdminAiKbController {

    private final AiKbClient aiKbClient;

    private final ProductMapper productMapper;

    @Operation(summary = "知识库与索引状态")
    @GetMapping("/stats")
    public BaseResponse<AiKbStatsVO> stats() {
        AiKbStatsVO stats = aiKbClient.stats();
        // 在售商品数由 Java 自己数：Python 只知道索引里有多少条，
        // 两边一比才能暴露"上了新商品但索引没跟上"（评估清单 2.3 的病灶）
        stats.setOnSaleProductCount(productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)));
        return ResultUtils.success(stats);
    }

    @Operation(summary = "近 N 日 AI 运营汇总（对话量/拒答率/工具分布/按日趋势）")
    @GetMapping("/ops")
    public BaseResponse<AiOpsSummaryVO> ops(@RequestParam(defaultValue = "7") int days) {
        return ResultUtils.success(aiKbClient.ops(days));
    }

    @Operation(summary = "FAQ 列表（分页 + 分类筛选）")
    @GetMapping("/faqs")
    public BaseResponse<AiFaqPageVO> listFaqs(@RequestParam(required = false) String category,
                                              @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(aiKbClient.listFaqs(category, pageNum, pageSize));
    }

    @Operation(summary = "新增 FAQ（保存即增量入索引）")
    @OperationLog(module = "AI 知识库", action = "新增 FAQ", content = "新增知识：#{#request.question}")
    @PostMapping("/faqs")
    public BaseResponse<AiFaqCreateVO> createFaq(@RequestBody AiFaqRequest request) {
        validate(request, true);
        return ResultUtils.success(aiKbClient.createFaq(request));
    }

    @Operation(summary = "编辑 FAQ（含启停；停用即从索引下架）")
    @OperationLog(module = "AI 知识库", action = "编辑 FAQ", content = "编辑知识 #{#id}")
    @PutMapping("/faqs/{id}")
    public BaseResponse<Boolean> updateFaq(@PathVariable Long id, @RequestBody AiFaqRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "FAQ id 不合法");
        // data 是"向量索引同步是否成功"（停用除名成功也算成功），前端只在 false 时提示重建
        boolean syncOk = aiKbClient.updateFaq(id, request);
        return ResultUtils.success(syncOk);
    }

    @Operation(summary = "删除 FAQ（同时从索引除名）")
    @OperationLog(module = "AI 知识库", action = "删除 FAQ", content = "删除知识 #{#id}")
    @DeleteMapping("/faqs/{id}")
    public BaseResponse<Void> deleteFaq(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "FAQ id 不合法");
        aiKbClient.deleteFaq(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "全量重建 FAQ 索引")
    @OperationLog(module = "AI 知识库", action = "重建 FAQ 索引", content = "全量重建 FAQ 向量索引")
    @PostMapping("/index")
    public BaseResponse<Long> rebuildFaqIndex() {
        return ResultUtils.success(aiKbClient.rebuildFaqIndex());
    }

    @Operation(summary = "全量重建商品召回索引")
    @OperationLog(module = "AI 知识库", action = "重建商品索引", content = "全量重建商品召回索引")
    @PostMapping("/products/index")
    public BaseResponse<Long> rebuildProductIndex() {
        return ResultUtils.success(aiKbClient.rebuildProductIndex());
    }

    /**
     * 基本校验放在 Java 侧：Python 也会校验，但在这里挡住才能给出中文提示、
     * 并且少一次注定失败的跨服务调用。
     */
    private void validate(AiFaqRequest request, boolean create) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        ThrowUtils.throwIf(create && (request.getQuestion() == null || request.getQuestion().isBlank()),
                ErrorCode.PARAMS_ERROR, "标准问法不能为空");
        ThrowUtils.throwIf(create && (request.getAnswer() == null || request.getAnswer().isBlank()),
                ErrorCode.PARAMS_ERROR, "答案不能为空");
        ThrowUtils.throwIf(request.getQuestion() != null && request.getQuestion().length() > 255,
                ErrorCode.PARAMS_ERROR, "标准问法过长（最多 255 字）");
        ThrowUtils.throwIf(request.getKeywords() != null && request.getKeywords().length() > 255,
                ErrorCode.PARAMS_ERROR, "关键词过长（最多 255 字）");
    }
}
