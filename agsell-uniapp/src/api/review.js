import { request } from '../utils/request'

/** 提交评价 */
export function createReview(data) {
  return request('POST', '/review', data)
}

/** 商品评价列表（无需登录） */
export function getProductReviews(productId, pageNum = 1, pageSize = 10) {
  return request('GET', `/review/product/${productId}`, null, { params: { pageNum, pageSize } })
}

/** 我的评价（需登录） */
export function getMyReviews(pageNum = 1, pageSize = 10) {
  return request('GET', '/review/my', null, { params: { pageNum, pageSize } })
}

export default {
  createReview,
  getProductReviews,
  getMyReviews
}
