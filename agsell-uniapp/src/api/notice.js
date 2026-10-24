import { request } from '../utils/request'

// ========== 首页公告（游客可见） ==========

/** 展示中的公告列表（sort 降序） */
export function getNoticeList() {
  return request('GET', '/notice/list')
}
