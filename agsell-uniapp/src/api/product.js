import { request } from '../utils/request'

/** 分类列表（无需登录） */
export function getCategoryList() {
  return request('GET', '/product/category/list')
}

/** 商品列表（需登录） */
export function getProductList(params) {
  return request('GET', '/product/list', null, { params })
}

/** 商品详情（无需登录） */
export function getProductDetail(id) {
  return request('GET', `/product/detail/${id}`)
}

/** 热销商品（需登录） */
export function getHotProducts(limit = 10) {
  return request('GET', '/product/hot', null, { params: { limit } })
}

/** AI 个性化推荐 feed（游客可用，服务端自动回退热销） */
export function getRecommendFeed(limit = 10) {
  return request('GET', '/product/recommend/feed', null, { params: { limit } })
}

/** 新品推荐（需登录） */
export function getNewProducts(limit = 10) {
  return request('GET', '/product/new', null, { params: { limit } })
}

/** 商品搜索（全文检索+高亮，需登录） */
export function searchProducts(params) {
  return request('GET', '/product/search', null, { params })
}

/** 热门搜索词（无需登录） */
export function getHotWords(limit = 10) {
  return request('GET', '/product/search/hot', null, { params: { limit } })
}

/**
 * 上报一次商品浏览（AI 个性化推荐的实时行为信号）
 * 纯埋点：失败/游客都静默，绝不允许给用户的访问带来任何可见影响
 */
export function reportProductView(productId) {
  return request('POST', '/product/view', null, { params: { productId }, timeout: 5000 })
}

export default {
  getCategoryList,
  getProductList,
  getProductDetail,
  getHotProducts,
  getNewProducts,
  searchProducts,
  getHotWords,
  reportProductView
}
