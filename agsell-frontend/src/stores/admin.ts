import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AdminInfoVO } from '@/types'
import { getAdminInfo } from '@/api/admin'
import { clearDynamicRoutes } from '@/router'

export const useAdminStore = defineStore('admin', () => {
  const token = ref<string>(localStorage.getItem('admin_token') ?? '')
  const adminInfo = ref<AdminInfoVO | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value)

  /** 同步 token，持久化到 localStorage */
  function setToken(t: string) {
    token.value = t
    localStorage.setItem('admin_token', t)
  }

  function setAdminInfo(info: AdminInfoVO) {
    adminInfo.value = info
  }

  /** 获取当前管理员信息，用于路由守卫初始化登录态 */
  async function fetchAdminInfo() {
    if (!token.value) return false
    loading.value = true
    try {
      const res = await getAdminInfo()
      adminInfo.value = res
      return true
    } catch {
      // token 失效，清除
      logout()
      return false
    } finally {
      loading.value = false
    }
  }

  function logout() {
    token.value = ''
    adminInfo.value = null
    localStorage.removeItem('admin_token')
    clearDynamicRoutes()
  }

  return { token, adminInfo, loading, isLoggedIn, setToken, setAdminInfo, fetchAdminInfo, logout }
})
