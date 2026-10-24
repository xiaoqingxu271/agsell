package com.lichun.agsell.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户站内消息 VO（小程序消息中心）
 */
@Data
@Schema(description = "用户站内消息")
public class UserMessageVO {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "消息类型：order=订单/物流 after_sales=售后结果")
    private String type;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "关联单号（订单号/售后单号）")
    private String relatedNo;

    @Schema(description = "0=未读 1=已读")
    private Integer isRead;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
