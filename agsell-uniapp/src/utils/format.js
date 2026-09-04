// 格式化工具

/**
 * 格式化价格
 */
export function formatPrice(price) {
  if (price === null || price === undefined) return '0.00'
  return Number(price).toFixed(2)
}

/**
 * 格式化时间
 */
export function formatDate(dateStr, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hour)
    .replace('mm', minute)
    .replace('ss', second)
}

/**
 * 订单状态文本
 */
export const ORDER_STATUS_TEXT = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消',
  5: '售后中'
}

/**
 * 订单状态颜色
 */
export const ORDER_STATUS_COLOR = {
  0: '#FF9800',
  1: '#2196F3',
  2: '#9C27B0',
  3: '#4CAF50',
  4: '#999999',
  5: '#F44336'
}

export function getOrderStatusText(status) {
  return ORDER_STATUS_TEXT[status] || '未知'
}

export function getOrderStatusColor(status) {
  return ORDER_STATUS_COLOR[status] || '#999'
}

/**
 * 评分转星星数
 */
export function getStarCount(rating) {
  return rating || 0
}

export default {
  formatPrice,
  formatDate,
  getOrderStatusText,
  getOrderStatusColor,
  getStarCount
}
