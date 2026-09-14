package com.lichun.agsell.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 商品搜索结果 VO：列表字段 + 关键词高亮
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductSearchVO extends ProductListItemVO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 名称高亮（<em> 包裹） */
    private String nameHighlight;

    /** 副标题高亮（<em> 包裹） */
    private String subtitleHighlight;
}
