package com.lichun.agsell.model.vo;

import lombok.Data;

/**
 * 新增 FAQ 的结果（id + 索引同步是否成功）。
 *
 * 为什么不只回 id：Python 的 POST /v1/admin/kb/faqs 返回 {id, indexed}，
 * 只取 id 就把 indexed 丢了，前端只能无条件弹"已新增并同步到向量索引"——
 * 而向量库挂掉时那句提示是假的（AI 会继续按旧知识库回答，管理员却以为生效了）。
 * 口径与 PUT 一致：boolean 表示"同步成功"，不是"这条现在在不在索引里"。
 */
@Data
public class AiFaqCreateVO {

    private Long id;

    /** false = 条目已写进 MySQL 但没进向量索引，需要点「重建 FAQ 索引」修复 */
    private boolean indexed;
}
