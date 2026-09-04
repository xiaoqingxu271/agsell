import { request, wxLogin } from '../utils/request'

// ========== 用户相关接口 ==========

/** 小程序登录 */
export function login(code) {
  return request('POST', '/user/login', { code })
}

/** 获取当前用户信息 */
export function getUserInfo() {
  return request('GET', '/user/info')
}

/** 更新个人信息 */
export function updateUserInfo(data) {
  return request('PUT', '/user/info', data)
}

/** 退出登录 */
export function logout() {
  return request('POST', '/user/logout')
}

/** 发送短信验证码 */
export function sendSmsCode(phone) {
  return request('POST', '/sms/send', null, { params: { phone } })
}

/** 获取地址列表 */
export function getAddressList() {
  return request('GET', '/user/address/list')
}

/** 新增地址 */
export function addAddress(data) {
  return request('POST', '/user/address', data)
}

/** 更新地址 */
export function updateAddress(id, data) {
  return request('PUT', `/user/address/${id}`, data)
}

/** 删除地址 */
export function deleteAddress(id) {
  return request('DELETE', `/user/address/${id}`)
}

/** 设置默认地址 */
export function setDefaultAddress(id) {
  return request('POST', `/user/address/${id}/default`)
}

export default {
  login,
  getUserInfo,
  updateUserInfo,
  logout,
  sendSmsCode,
  getAddressList,
  addAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress
}
