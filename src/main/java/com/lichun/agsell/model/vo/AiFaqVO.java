package com.lichun.agsell.model.vo;

import lombok.Data;

/**
 * AI 知识库 FAQ 条目（由 ai-service /v1/admin/kb/faqs 返回，Java 只做驼峰映射）。
 */
@Data
public class AiFaqVO {

    private Long id;
    private String question;
    private String answer;
    private String category;
    private String keywords;
    private Integer status;
    private String updatedAt;
}
