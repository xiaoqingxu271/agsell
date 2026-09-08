import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
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
    if (code === 0) return response.data.data as unknown as AxiosResponse
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

// ─── 类型修正 ────────────────────────────────────────────────────────────────
// 响应拦截器已在运行时解包 response.data.data，但 axios 实例类型仍视为返回
// AxiosResponse，导致调用方 request.get<T>() 类型与运行时不一致。这里重新声明
// 泛型方法签名，使 request.get<T>() 直接返回 Promise<T>（运行时行为不变）。
interface RequestInstance {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const request = service as unknown as RequestInstance
export default request
