package com.lichun.agsell.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 近 7 日被 AI 引用的知识条目（运营视图）
 * <p>
 * Python 侧按 kb_hit_log（state.references 落库）聚合，Java 只透传——
 * 引用数据让"知识闭环"有运营侧的证据：哪条知识真的被用过。
 */
@Data
@Schema(description = "近 7 日被 AI 引用的知识条目")
public class KbHitTopVO {

    @Schema(description = "FAQ 条目 ID")
    private Long faqId;

    @Schema(description = "条目问法")
    private String question;

    @Schema(description = "近 7 日引用次数")
    private Long hits;
}
