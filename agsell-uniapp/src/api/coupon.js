import { request } from '../utils/request'

/** 领券中心：可领券列表（无需登录，已领标记仅登录时返回） */
export function getCouponList() {
  return request('GET', '/coupon/list')
}

/** 领取优惠券 */
export function receiveCoupon(id) {
  return request('POST', `/coupon/${id}/receive`)
}

/** 我的券包（status=0 未使用 1 已使用 2 已过期，不传查全部） */
export function getMyCoupons(params = {}) {
  return request('GET', '/coupon/my', null, { params })
}

/** 结算页可用券（按订单金额+商品范围过滤；productIds 逗号分隔，品类券用） */
export function getAvailableCoupons(totalAmount, productIds) {
  const params = { totalAmount }
  if (productIds && productIds.length) {
    params.productIds = productIds.join(',')
  }
  return request('GET', '/coupon/available', null, { params })
}

export default {
  getCouponList,
  receiveCoupon,
  getMyCoupons,
  getAvailableCoupons
}
