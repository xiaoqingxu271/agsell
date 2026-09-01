package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增/更新轮播图请求
 */
@Data
public class BannerCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 标题 */
    private String title;

    /** 图片URL */
    private String image;

    /** 跳转链接 */
    private String link;

    /** 排序值 */
    private Integer sort;

    /** 状态 0=禁用 1=启用 */
    private Integer status;
}
