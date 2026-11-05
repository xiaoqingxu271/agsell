package com.lichun.agsell.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 已入库文档清单（按来源聚合，每份文档一个条目）。
 *
 * 文档切块入库后每块是一条独立的 ai_faq，FAQ 列表页里会显得零碎；
 * 这里按 source 聚合回"文档"视角，管理页据此展示「文档知识」卡片与删除入口。
 */
@Data
public class AiDocListVO {

    private List<Item> items;

    @Data
    public static class Item {

        /** 文档名（上传时的标题，不含 doc: 前缀） */
        private String name;

        /** 切出的块数 */
        private Long chunks;
    }
}
