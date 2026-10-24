import { request } from '../utils/request'

// ========== 商品收藏（同时是 AI 偏好的一路信号） ==========

/** 收藏商品（幂等） */
export function addFavorite(productId) {
  return request('POST', `/favorite/${productId}`)
}

/** 取消收藏（幂等） */
export function removeFavorite(productId) {
  return request('DELETE', `/favorite/${productId}`)
}

/** 我的收藏分页（收藏时间倒序，带商品快照） */
export function getFavorites(pageNum = 1, pageSize = 20) {
  return request('GET', '/favorite/list', null, { params: { pageNum, pageSize } })
}

/** 是否已收藏 */
export function isFavored(productId) {
  return request('GET', '/favorite/exists', null, { params: { productId } })
}
