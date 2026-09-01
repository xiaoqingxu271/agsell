package com.lichun.agsell.service;

public interface PaymentService {

    /**
     * 创建支付（模拟）
     */
    void createPayment(String orderNo);

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
