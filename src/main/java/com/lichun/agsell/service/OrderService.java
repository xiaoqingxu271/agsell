package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.OrderCreateRequest;
import com.lichun.agsell.model.vo.OrderCreateVO;
import com.lichun.agsell.model.vo.OrderDetailVO;
import com.lichun.agsell.model.vo.OrderListItemVO;

public interface OrderService {

    /**
     * 提交订单
     */
    OrderCreateVO createOrder(OrderCreateRequest request);

    /**
     * 我的订单列表
     */
    Page<OrderListItemVO> listOrders(int pageNum, int pageSize, Integer status);

    /**
     * 订单详情
     */
    OrderDetailVO getOrderDetail(String orderNo);

    /**
     * 取消订单
     */
    void cancelOrder(String orderNo, String reason);

    /**
     * 确认收货
     */
    void confirmReceive(String orderNo);
}
