package com.lichun.agsell.model.dto;

import lombok.Data;

/**
 * AI 知识库（FAQ）新增/编辑请求。
 *
 * 字段口径与 ai-service 的 FaqCreate/FaqUpdate 一致（question/answer/category/keywords/status），
 * Java 侧只做转发与基本校验，不另存一份数据——MySQL ai_faq 表由 Python 服务读写，
 * 避免两处都能改同一张表而产生的口径分叉。
 */
@Data
public class AiFaqRequest {

    /** 标准问法（必填，1~255 字） */
    private String question;

    /** 标准答案（必填） */
    private String answer;

    /** 分类，如 shipping/after_sales/product，默认 general */
    private String category;

    /** 关键词（逗号分隔），双路召回时每个命中 +0.5 分 */
    private String keywords;

    /** 1 启用 / 0 停用；停用会同步从向量索引下架 */
    private Integer status;
}
