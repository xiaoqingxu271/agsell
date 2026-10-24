import { request } from '../utils/request'

// ========== 消息中心（发货/售后处理结果通知） ==========

/** 我的消息分页 */
export function getMessages(pageNum = 1, pageSize = 20) {
  return request('GET', '/message/list', null, { params: { pageNum, pageSize } })
}

/** 未读消息数（首页铃铛红点） */
export function getUnreadCount() {
  return request('GET', '/message/unread-count')
}

/** 全部标记已读 */
export function markAllRead() {
  return request('POST', '/message/read-all')
}
