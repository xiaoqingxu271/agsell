// 全局请求封装
// API 地址统一从环境文件读取：改 agsell-uniapp/.env.mp-weixin 里的 VITE_API_BASE_URL 即可
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

/**
 * 统一处理非 0 的响应码，返回 false 表示业务失败，不抛出异常
 * 40100/10001 时静默清除登录态，不弹窗（避免游客模式死循环）
 */
export function request(method, url, data = null, extra = {}) {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    const header = {
      'Content-Type': 'application/json',
      ...extra.header
    }
    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    // 任意方法（GET/PUT/DELETE 等）：将 params 拼接到 URL 查询字符串
    let requestUrl = BASE_URL + url
    if (extra.params) {
      const pairs = []
      for (const [key, value] of Object.entries(extra.params)) {
        if (value !== null && value !== undefined && value !== '') {
          pairs.push(encodeURIComponent(key) + '=' + encodeURIComponent(value))
        }
      }
      if (pairs.length > 0) {
        requestUrl += (url.includes('?') ? '&' : '?') + pairs.join('&')
      }
    }

    uni.request({
      url: requestUrl,
      method,
      data: method === 'GET' ? undefined : data,
      header,
      timeout: extra.timeout || 8000,
      success: (res) => {
        const { data: result } = res
        if (result.code === 40100 || result.code === 10001) {
          // Token 失效，静默清除登录态，不弹窗（避免游客模式死循环）
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          console.warn('[Request] Token 失效，已清除登录态')
          reject(result)
          return
        }
        resolve(result)
      },
      fail: (err) => {
        console.warn('网络请求失败', err)
        reject(err)
      }
    })
  })
}

// 小程序登录（返回 Promise，失败时静默返回 false，不弹框）
export async function wxLogin() {
  try {
    const { code } = await uni.login()
    if (!code) return false
    const res = await request('POST', '/user/login', { code })
    if (res.code === 0 && res.data) {
      const { token, userId, nickname, avatar, phone } = res.data
      uni.setStorageSync('token', token)
      uni.setStorageSync('userInfo', { userId, nickname, avatar, phone })
      console.log('[wxLogin] 登录成功, userId:', userId)
      return true
    }
    return false
  } catch (e) {
    console.warn('登录失败（游客模式或网络异常），继续以未登录状态使用', e)
    return false
  }
}

// 检查是否已登录
export function isLoggedIn() {
  return !!uni.getStorageSync('token')
}

/**
 * 用系统配置的平台名称覆盖当前页导航栏标题（首页/分类/购物车/我的 四个 tab 统一调用）。
 * 优先用本地缓存立即设置，再请求最新配置更新；未配置或失败时保留 pages.json 默认标题。
 */
export async function setPlatformTitle() {
  try {
    const cached = uni.getStorageSync('platform_name')
    if (cached) uni.setNavigationBarTitle({ title: cached })
    const res = await request('GET', '/system/config', null, { params: { keys: 'platform_name' } })
    if (res.code === 0 && res.data?.platform_name) {
      uni.setStorageSync('platform_name', res.data.platform_name)
      uni.setNavigationBarTitle({ title: res.data.platform_name })
    }
  } catch (e) {
    // 静默失败，保留默认标题
  }
}

export default { request, wxLogin, isLoggedIn, setPlatformTitle }
