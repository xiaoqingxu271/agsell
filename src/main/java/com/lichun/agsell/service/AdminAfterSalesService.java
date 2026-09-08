package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.AdminAfterSalesHandleRequest;
import com.lichun.agsell.model.vo.AdminAfterSalesDetailVO;
import com.lichun.agsell.model.vo.AdminAfterSalesListItemVO;

/**
 * 管理端售后 Service
 */
public interface AdminAfterSalesService {

    /**
     * 售后单列表（分页+状态/订单号/用户名筛选）
     */
    Page<AdminAfterSalesListItemVO> list(int pageNum, int pageSize, Integer status,
                                         String afterSalesNo, String username);

    /**
     * 售后单详情
     */
    AdminAfterSalesDetailVO getDetail(String afterSalesNo);

    /**
     * 处理售后（同意退款/拒绝）
     * 同意：订单关闭、回补库存、已完成订单回滚销量
     * 拒绝：订单恢复原状态
     */
    void handle(String afterSalesNo, AdminAfterSalesHandleRequest request);
}
