// 全局请求封装
// API 地址来自 agsell-uniapp/.env 的 VITE_API_BASE_URL（编译期烤进产物，改后需重新构建）
export const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

/**
 * 兜底非 JSON 响应与内部异常文案，页面拿到的 message 永远是用户能看懂的话术
 * - 后端 50000 是全局异常兜底码，原文"系统错误"对使用者无意义且惊吓，换成友好提示
 * - 网关/HTML 错误页等解析不出 code 时，统一成网络异常文案
 */
function normalizeResult(raw) {
  if (!raw || typeof raw !== 'object' || raw.code === undefined) {
    return { code: -1, data: null, message: '网络开小差了，请稍后重试' }
  }
  if (raw.code === 50000) {
    return { ...raw, message: '服务开小差了，请稍后重试' }
  }
  return raw
}

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
        const { data: raw } = res
        if (raw && (raw.code === 40100 || raw.code === 10001)) {
          // Token 失效，静默清除登录态，不弹窗（避免游客模式死循环）
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          console.warn('[Request] Token 失效，已清除登录态')
          reject(raw)
          return
        }
        resolve(normalizeResult(raw))
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
