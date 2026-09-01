package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.OrderShipRequest;
import com.lichun.agsell.model.vo.AdminOrderDetailVO;
import com.lichun.agsell.model.vo.AdminOrderListItemVO;

public interface AdminOrderService {

    /**
     * 订单列表
     */
    Page<AdminOrderListItemVO> listOrders(int pageNum, int pageSize, Integer status, String orderNo, String username);

    /**
     * 订单详情
     */
    AdminOrderDetailVO getOrderDetail(String orderNo);

    /**
     * 发货
     */
    void shipOrder(String orderNo, OrderShipRequest request);
}
