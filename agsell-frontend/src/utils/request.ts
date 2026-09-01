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
    const userToken = localStorage.getItem('user_token')
    if (userToken) config.headers.Authorization = `Bearer ${userToken}`
    const adminToken = localStorage.getItem('admin_token')
    if (adminToken) config.headers.Authorization = `Bearer ${adminToken}`
    return config
  },
  (error) => Promise.reject(error),
)

// ─── 响应拦截器 ──────────────────────────────────────────────────────────────

service.interceptors.response.use(
  (response: AxiosResponse<BaseResponse>) => {
    const { code, message } = response.data
    if (code === 0) return response.data.data
    if (message) ElMessage.error(message)
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
