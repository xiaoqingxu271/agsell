package com.lichun.agsell.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 文档上传入库结果（切块数 + 索引同步 + 替换的旧块数）。
 *
 * indexed 与 FAQ 口径一致：false = 块已写进 MySQL 但没进向量索引，
 * 管理页点「重建 FAQ 索引」即可修复；replaced &gt; 0 表示同名旧文档被整批替换。
 */
@Data
public class AiDocUploadVO {

    /** 入库时使用的文档名（标题） */
    private String name;

    /** 本次切出的块数（每块一条 ai_faq，category=doc_knowledge） */
    private Long chunks;

    /** 被替换的旧块数（重复上传同名文档时 &gt; 0） */
    private Long replaced;

    /** false = 索引同步失败，需要重建 */
    private boolean indexed;
}
