import { request } from '../utils/request'

/** 加入购物车 */
export function addToCart(data) {
  return request('POST', '/cart/add', data)
}

/** 购物车列表 */
export function getCartList() {
  return request('GET', '/cart/list')
}

/** 修改数量 */
export function updateCartQuantity(id, quantity) {
  return request('PUT', `/cart/item/${id}`, { quantity })
}

/** 删除商品 */
export function deleteCartItem(id) {
  return request('DELETE', `/cart/item/${id}`)
}

/** 选中/取消选中 */
export function toggleCartSelect(id) {
  return request('PUT', `/cart/select/${id}`)
}

/** 全选/取消全选 */
export function selectAllCart(selected) {
  return request('PUT', '/cart/select-all', null, { params: { selected } })
}

/** 批量删除 */
export function batchDeleteCart(ids) {
  return request('DELETE', '/cart/batch', ids)
}

export default {
  addToCart,
  getCartList,
  updateCartQuantity,
  deleteCartItem,
  toggleCartSelect,
  selectAllCart,
  batchDeleteCart
}
