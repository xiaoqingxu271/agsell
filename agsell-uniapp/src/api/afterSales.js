import { request } from '../utils/request'

/** 申请售后 */
export function applyAfterSales(data) {
  return request('POST', '/after-sales', data)
}

/** 我的售后列表 */
export function getAfterSalesList(params) {
  return request('GET', '/after-sales/list', null, { params })
}

/** 售后详情 */
export function getAfterSalesDetail(afterSalesNo) {
  return request('GET', `/after-sales/${afterSalesNo}`)
}

/** 撤销售后申请 */
export function cancelAfterSales(afterSalesNo) {
  return request('POST', `/after-sales/${afterSalesNo}/cancel`)
}

export default {
  applyAfterSales,
  getAfterSalesList,
  getAfterSalesDetail,
  cancelAfterSales
}
