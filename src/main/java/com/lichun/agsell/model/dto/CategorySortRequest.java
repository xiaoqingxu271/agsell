package com.lichun.agsell.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 分类排序批量保存请求（管理端拖拽排序后整批提交）
 */
@Data
@Schema(description = "分类排序批量保存请求")
public class CategorySortRequest {

    @Schema(description = "排序项列表（id + 新排序值）")
    private List<SortItem> items;

    @Data
    @Schema(description = "单个分类的排序项")
    public static class SortItem {

        @Schema(description = "分类 ID")
        private Long id;

        @Schema(description = "新排序值（越大越前）")
        private Integer sort;
    }
}
