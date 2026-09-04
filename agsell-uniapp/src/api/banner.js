import { request } from '../utils/request'

/** 轮播图列表（无需登录） */
export function getBannerList() {
  return request('GET', '/banner/list')
}

export default { getBannerList }
