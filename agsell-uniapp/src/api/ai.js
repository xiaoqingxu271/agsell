import { request } from '../utils/request'

/**
 * AI 智能客服对话
 * 调用链：小程序 → Java /api/ai/chat（JWT 取 userId）→ Python /v1/chat
 * AI 生成 + 业务查询较慢，超时放宽到 60s
 */
export function sendAiChat(sessionId, message) {
  return request('POST', '/ai/chat', { sessionId, message }, { timeout: 60000 })
}

export default { sendAiChat }
