package com.lichun.agsell.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 知识库 FAQ 分页结果（Python 返回 {total, items}，这里映射成前端表格要的形状）。
 */
@Data
public class AiFaqPageVO {

    private long total;

    private List<AiFaqVO> items = new ArrayList<>();
}
