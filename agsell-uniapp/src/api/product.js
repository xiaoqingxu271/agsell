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

/** 新品推荐（需登录） */
export function getNewProducts(limit = 10) {
  return request('GET', '/product/new', null, { params: { limit } })
}

export default {
  getCategoryList,
  getProductList,
  getProductDetail,
  getHotProducts,
  getNewProducts
}
