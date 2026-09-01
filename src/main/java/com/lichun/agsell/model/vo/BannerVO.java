package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 轮播图 VO
 */
@Data
public class BannerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String image;

    private String link;

    private Integer sort;

    private Integer status;

    private LocalDateTime createTime;
}
