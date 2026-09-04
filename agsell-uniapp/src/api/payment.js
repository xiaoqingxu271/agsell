import { request } from '../utils/request'

/** 模拟支付 */
export function createPayment(orderNo) {
  return request('POST', '/payment/create', { orderNo })
}

/** 查询支付状态 */
export function getPaymentStatus(orderNo) {
  return request('GET', `/payment/${orderNo}`)
}

export default {
  createPayment,
  getPaymentStatus
}
