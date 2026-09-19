package com.lichun.agsell.service;

import com.lichun.agsell.model.vo.NoticeSummaryVO;

/**
 * 管理端通知中心
 */
public interface AdminNoticeService {

    /** 汇总待办：待发货订单 + 低库存商品 + 待处理售后 */
    NoticeSummaryVO summary();
}
