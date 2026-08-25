import request from '@/utils/request'
import type { BaseResponse } from '@/types'

/** 健康检查 */
export function checkHealth() {
  return request.get<BaseResponse<string>>('/health')
}
