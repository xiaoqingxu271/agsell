import { request } from '../utils/request'

/** 提交订单 */
export function createOrder(data) {
  return request('POST', '/order/create', data)
}

/** 我的订单列表 */
export function getOrderList(params) {
  return request('GET', '/order/list', null, { params })
}

/** 订单详情 */
export function getOrderDetail(orderNo) {
  return request('GET', `/order/${orderNo}`)
}

/** 取消订单 */
export function cancelOrder(orderNo, reason) {
  return request('POST', `/order/${orderNo}/cancel`, null, { params: { reason } })
}

/** 确认收货 */
export function confirmReceive(orderNo) {
  return request('POST', `/order/${orderNo}/confirm`)
}

export default {
  createOrder,
  getOrderList,
  getOrderDetail,
  cancelOrder,
  confirmReceive
}
