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
 * 上游 error 事件的 code → 用户可读话术（白名单在前端，绝不定位到服务端文案）：
 * 服务端只发 code，异常详情仅进日志，避免 429/网关/模型信息直出气泡
 */
const AI_ERROR_TEXT = {
  ai_upstream: 'AI 服务这会儿有点忙，请稍后再试',
  ai_unavailable: '服务暂时不可用，请稍后再试'
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
 *   - onItems(items)           导购推荐的结构化商品卡（候选一确定就下发，不必等回复说完）
 *   - onDone({suggestions,items}) 正常收尾；items 再带一份兜底（只监听 done 也能渲染卡片）
 *   - onError(err)             上游 error 事件（err.message 为提示文案）
 * @param {number|string} [productId] 从商品详情页进客服时携带，服务端据此把"这个"指代到该商品
 * @returns {Promise<void>} 流结束 resolve；传输失败 / 非 SSE 响应（如 token 失效 40100）reject
 */
export function sendAiChatStream(sessionId, message, handlers = {}, productId = null) {
  const token = uni.getStorageSync('token')
  const header = {}
  if (token) header.Authorization = `Bearer ${token}`

  const data = { sessionId, message }
  // 只在有值时携带：服务端按"缺字段=不注入商品上下文"处理，传 null 反而会被 pydantic 拒
  if (productId) data.productId = productId

  return sseRequest({
    url: '/ai/chat/stream',
    data,
    header,
    timeout: 120000,
    onEvent: (event, data) => {
      if (event === 'token') {
        handlers.onToken?.(data.content || '')
      } else if (event === 'status') {
        handlers.onStatus?.(data)
      } else if (event === 'items') {
        handlers.onItems?.(Array.isArray(data.items) ? data.items : [])
      } else if (event === 'done') {
        handlers.onDone?.(data || {})
      } else if (event === 'error') {
        const err = new Error(AI_ERROR_TEXT[data && data.code] || '抱歉，服务开小差了，请稍后再试')
        err.code = (data && data.code) || 'ai_unknown'
        handlers.onError?.(err)
      }
    }
  })
}

export default { sendAiChat, sendAiChatStream }
