import { request } from '../utils/request'

/** 模拟支付：payType 1=支付宝 2=微信支付 */
export function createPayment(orderNo, payType) {
  return request('POST', '/payment/create', { orderNo, payType })
}

/** 查询支付状态 */
export function getPaymentStatus(orderNo) {
  return request('GET', `/payment/${orderNo}`)
}

export default {
  createPayment,
  getPaymentStatus
}
