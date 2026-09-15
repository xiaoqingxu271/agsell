package com.lichun.agsell.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志响应项
 */
@Data
public class SysLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long adminId;

    private String adminName;

    private String module;

    private String action;

    private String content;

    private String ip;

    private LocalDateTime createTime;
}
