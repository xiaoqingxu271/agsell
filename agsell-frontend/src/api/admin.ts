import request from '@/utils/request'
import type {
  AdminInfoVO,
  AdminLoginRequest,
  AdminLoginVO,
  AdminUserListItemVO,
  AdminUserStatusRequest,
  BaseResponse,
  Page,
} from '@/types'

/** 管理员登录 */
export function adminLogin(data: AdminLoginRequest) {
  return request.post<BaseResponse<AdminLoginVO>>('/admin/login', data)
}

/** 获取当前管理员信息 */
export function getAdminInfo() {
  return request.get<BaseResponse<AdminInfoVO>>('/admin/info')
}

/** 管理员退出登录 */
export function adminLogout() {
  return request.post<BaseResponse<null>>('/admin/logout')
}

/** 用户列表 */
export function listUsers(params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
}) {
  return request.get<BaseResponse<Page<AdminUserListItemVO>>>('/admin/user/list', { params })
}

/** 禁用/启用用户 */
export function updateUserStatus(id: number, data: AdminUserStatusRequest) {
  return request.put<BaseResponse<null>>(`/admin/user/${id}/status`, data)
}
