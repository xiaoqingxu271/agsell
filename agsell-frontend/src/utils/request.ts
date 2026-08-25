import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { BaseResponse } from '@/types'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// ─── 请求拦截器 ──────────────────────────────────────────────────────────────

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 用户 Token
    const userToken = localStorage.getItem('user_token')
    if (userToken) {
      config.headers.Authorization = `Bearer ${userToken}`
    }
    // 管理员 Token
    const adminToken = localStorage.getItem('admin_token')
    if (adminToken) {
      config.headers.Authorization = `Bearer ${adminToken}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

// ─── 响应拦截器 ──────────────────────────────────────────────────────────────

service.interceptors.response.use(
  (response: AxiosResponse<BaseResponse>) => {
    const { code, message } = response.data

    if (code === 0) {
      return response.data
    }

    // 业务错误提示
    if (message) {
      ElMessage.error(message)
    }

    // 未登录 / Token 过期 → 清除本地 token 并跳转
    if (code === 40100 || code === 40200) {
      localStorage.removeItem('user_token')
      localStorage.removeItem('admin_token')
      window.location.href = '/'
    }

    return Promise.reject(new Error(message ?? '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message ?? error.message

    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('user_token')
      localStorage.removeItem('admin_token')
      window.location.href = '/'
    } else {
      ElMessage.error(message ?? '网络异常')
    }

    return Promise.reject(error)
  },
)

export default service
