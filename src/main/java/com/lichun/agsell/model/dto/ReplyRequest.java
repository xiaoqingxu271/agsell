package com.lichun.agsell.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 回复评价请求
 */
@Data
public class ReplyRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 回复内容 */
    private String replyContent;
}
