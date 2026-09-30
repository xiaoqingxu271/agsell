import { request } from '../utils/request'
import { sseRequest } from '../utils/sse'

/**
 * AI 智能客服对话（同步整段返回）
 * 调用链：小程序 → Java /api/ai/chat（JWT 取 userId）→ Python /v1/chat
 * AI 生成 + 业务查询较慢，超时放宽到 60s
 */
export function sendAiChat(sessionId, message) {
  return request('POST', '/ai/chat', { sessionId, message }, { timeout: 60000 })
}

/**
 * AI 智能客服对话（SSE 流式，回复逐字下发）
 * 调用链：小程序 → Java /api/ai/chat/stream（SseEmitter 转发）→ Python /v1/chat/stream
 *
 * @param {string} sessionId 会话 ID
 * @param {string} message   用户消息
 * @param {Object} handlers  事件回调
 *   - onToken(text)            逐字回复片段
 *   - onStatus({stage,intent}) 业务阶段（faq_retrieve/order_query/logistics_query/after_sales_query）
 *   - onDone({suggestions})    正常收尾，携带下一轮建议问题
 *   - onError(err)             上游 error 事件（err.message 为提示文案）
 * @returns {Promise<void>} 流结束 resolve；传输失败 / 非 SSE 响应（如 token 失效 40100）reject
 */
export function sendAiChatStream(sessionId, message, handlers = {}) {
  const token = uni.getStorageSync('token')
  const header = {}
  if (token) header.Authorization = `Bearer ${token}`

  return sseRequest({
    url: '/ai/chat/stream',
    data: { sessionId, message },
    header,
    timeout: 120000,
    onEvent: (event, data) => {
      if (event === 'token') {
        handlers.onToken && handlers.onToken(data.content || '')
      } else if (event === 'status') {
        handlers.onStatus && handlers.onStatus(data)
      } else if (event === 'done') {
        handlers.onDone && handlers.onDone(data || {})
      } else if (event === 'error') {
        const err = new Error(data.message || 'AI 服务异常')
        handlers.onError && handlers.onError(err)
      }
    }
  })
}

export default { sendAiChat, sendAiChatStream }
