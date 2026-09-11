import { request } from '../utils/request'

/** 按批次号查询溯源档案（无需登录） */
export function getTraceByBatchNo(batchNo) {
  return request('GET', '/trace', null, { params: { batchNo } })
}

/** 商品溯源摘要（详情页溯源入口） */
export function getProductTraceSummary(productId) {
  return request('GET', `/trace/product/${productId}`)
}

export default {
  getTraceByBatchNo,
  getProductTraceSummary
}
