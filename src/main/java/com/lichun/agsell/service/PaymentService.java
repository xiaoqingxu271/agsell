package com.lichun.agsell.service;

public interface PaymentService {

    /** 支付方式：支付宝 */
    int PAY_TYPE_ALIPAY = 1;

    /** 支付方式：微信支付 */
    int PAY_TYPE_WECHAT = 2;

    /**
     * 创建支付（模拟）：支付直接成功，但将所选支付方式写入订单
     *
     * @param orderNo 订单号
     * @param payType 支付方式 1=支付宝 2=微信支付，为空默认支付宝
     */
    void createPayment(String orderNo, Integer payType);

    /**
     * 查询支付状态
     */
    PaymentStatusVO getPaymentStatus(String orderNo);

    /**
     * 支付状态 VO
     */
    class PaymentStatusVO {
        private String orderNo;
        private Integer status;
        private String statusText;
        private java.time.LocalDateTime payTime;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getStatusText() { return statusText; }
        public void setStatusText(String statusText) { this.statusText = statusText; }
        public java.time.LocalDateTime getPayTime() { return payTime; }
        public void setPayTime(java.time.LocalDateTime payTime) { this.payTime = payTime; }
    }
}
