package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.vo.UserMessageVO;

/**
 * 用户站内消息服务（发货/售后结果通知）
 */
public interface UserMessageService {

    /** 我的消息分页（时间倒序） */
    Page<UserMessageVO> pageMine(int pageNum, int pageSize);

    /** 未读消息数（首页铃铛红点） */
    long unreadCount();

    /** 全部标记已读，返回更新条数 */
    int markAllRead();

    /**
     * 写一条站内消息（发货/售后处理等管理员触发节点调用）
     *
     * @param userId    接收用户ID
     * @param type      消息类型 order/after_sales
     * @param title     标题
     * @param content   内容
     * @param relatedNo 关联单号（订单号/售后单号）
     */
    void notify(Long userId, String type, String title, String content, String relatedNo);
}
