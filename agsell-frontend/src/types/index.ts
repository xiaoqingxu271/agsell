/** 后端统一响应体 */
export interface BaseResponse<T = unknown> {
  code: number
  data: T
  message: string
}

/** 分页数据 */
export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// ─── 管理员相关类型 ──────────────────────────────────────────────────────────

export interface AdminLoginRequest {
  username: string
  password: string
}

export interface AdminLoginVO {
  token: string
  adminId: number
  realName: string
  role: string
}

export interface AdminInfoVO {
  adminId: number
  realName: string
  role: string
}

export interface AdminUserStatusRequest {
  /** 0=禁用 1=启用 */
  status: number
}

export interface AdminUserListItemVO {
  id: number
  username: string
  nickname: string
  avatar: string | null
  phone: string | null
  status: number
  loginTime: string | null
  createTime: string
}

// ─── 错误码枚举（与后端 ErrorCode 对齐）─────────────────────────────────────

export enum ErrorCode {
  SUCCESS = 0,
  PARAMS_ERROR = 40000,
  NOT_LOGIN_ERROR = 40100,
  NO_AUTH_ERROR = 40101,
  NOT_FOUND_ERROR = 40400,
  FORBIDDEN_ERROR = 40300,
  SYSTEM_ERROR = 50000,
  OPERATION_ERROR = 50001,
  ADMIN_NOT_LOGIN_ERROR = 40200,
  ADMIN_NO_AUTH_ERROR = 40201,
  PASSWORD_ERROR = 40301,
  USER_ALREADY_EXISTS = 40401,
  PHONE_ALREADY_EXISTS = 40402,
  SMS_CODE_INVALID = 40001,
  SMS_CODE_EXPIRED = 40002,
  SMS_CODE_SEND_TOO_FAST = 40003,
}
