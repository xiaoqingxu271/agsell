import { request } from '../utils/request'

/** 秒杀活动列表（filter=1 仅进行中；0 未开始+进行中；不传 全部） */
export function getSeckillList(params = {}) {
  return request('GET', '/seckill/list', null, { params })
}

/** 秒杀活动详情（无需登录，按活动编号） */
export function getSeckillDetail(code) {
  return request('GET', `/seckill/detail/${code}`)
}

/** 秒杀下单（需登录） */
export function createSeckillOrder(data) {
  return request('POST', '/seckill/order', data)
}

export default {
  getSeckillList,
  getSeckillDetail,
  createSeckillOrder
}
