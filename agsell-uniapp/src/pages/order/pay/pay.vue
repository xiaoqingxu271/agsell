<template>
  <view class="pay-page">
    <!-- 状态区 -->
    <view class="status-card" :class="{ expired: expired }">
      <view class="clock-icon-wrap" :class="{ expired: expired }">
        <view class="clock" :class="{ expired: expired }">
          <view class="clock-hand hour"></view>
          <view class="clock-hand minute"></view>
          <view class="clock-dot"></view>
        </view>
      </view>
      <text class="status-title">{{ expired ? '订单已超时关闭' : '等待支付' }}</text>
      <text class="pay-amount">¥{{ payAmount }}</text>
      <view v-if="!expired" class="countdown">
        <text class="countdown-label">剩余支付时间</text>
        <text class="countdown-time">{{ countdownText }}</text>
      </view>
      <view v-else class="expired-hint">该订单已超过支付时限，已被系统自动取消</view>
      <text class="order-no">订单号：{{ orderNo }}</text>
    </view>

    <!-- 提示区 -->
    <view class="tips-card">
      <view class="tip-row">
        <text class="tip-dot">•</text>
        <text class="tip-text">请在倒计时结束前完成支付，超时订单将自动取消</text>
      </view>
      <view class="tip-row">
        <text class="tip-dot">•</text>
        <text class="tip-text">库存将在支付成功后扣减，未支付不会占用库存</text>
      </view>
    </view>

    <!-- 底部操作 -->
    <view class="bottom-bar">
      <view class="btn-cancel" :class="{ disabled: expired || paying || cancelling }" @click="onCancel">取消支付</view>
      <view class="btn-pay" :class="{ disabled: expired || paying }" @click="onPay">{{ paying ? '支付中…' : '立即支付' }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { getOrderDetail, cancelOrder } from '../../../api/order'
import { createPayment } from '../../../api/payment'

const orderNo = ref('')
const payAmount = ref('0.00')
const expired = ref(false)
const paying = ref(false)
const cancelling = ref(false)

// 倒计时：以服务端下发的到期时间戳为准，切后台/回来用 onShow 重算
const deadline = ref(0)
const remainSeconds = ref(0)
const countdownText = ref('')
let timer = null

onLoad(async (options) => {
  orderNo.value = options.orderNo || ''
  payAmount.value = options.payAmount || '0.00'
  await loadOrder()
})

onShow(() => {
  if (deadline.value > 0 && !expired.value) {
    startCountdown()
  }
})

onHide(clearTimer)
onUnload(clearTimer)

async function loadOrder() {
  if (!orderNo.value) return
  const res = await getOrderDetail(orderNo.value)
  if (res.code !== 0) {
    uni.showToast({ title: res.message || '订单加载失败', icon: 'none' })
    return
  }
  const detail = res.data
  if (Number(detail.payAmount) > 0) payAmount.value = detail.payAmount
  // 状态已变化（已支付/已取消/已超时等）：跳转订单详情页展示真实状态
  if (detail.status !== 0) {
    uni.showToast({ title: '订单状态已变更', icon: 'none' })
    uni.redirectTo({ url: `/pages/order/detail/detail?orderNo=${orderNo.value}` })
    return
  }
  const seconds = Number(detail.expireSeconds || 0)
  if (seconds <= 0) {
    onExpired()
  } else {
    deadline.value = Date.now() + seconds * 1000
    startCountdown()
  }
}

function startCountdown() {
  clearTimer()
  const ms = deadline.value - Date.now()
  remainSeconds.value = Math.max(0, Math.ceil(ms / 1000))
  if (remainSeconds.value <= 0) {
    onExpired()
    return
  }
  countdownText.value = formatCountdown(remainSeconds.value)
  timer = setInterval(() => {
    const left = Math.max(0, Math.ceil((deadline.value - Date.now()) / 1000))
    remainSeconds.value = left
    if (left <= 0) {
      onExpired()
    } else {
      countdownText.value = formatCountdown(left)
    }
  }, 1000)
}

function formatCountdown(seconds) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const pad = (n) => String(n).padStart(2, '0')
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

function onExpired() {
  clearTimer()
  expired.value = true
  remainSeconds.value = 0
  countdownText.value = '00:00'
}

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function onPay() {
  if (expired.value || paying.value) return
  paying.value = true
  try {
    const res = await createPayment(orderNo.value)
    if (res.code === 0) {
      uni.redirectTo({
        url: `/pages/order/pay-success/pay-success?orderNo=${orderNo.value}&payAmount=${payAmount.value}`
      })
    } else {
      uni.showToast({ title: res.message || '支付失败', icon: 'none' })
      // 服务端拦截（如已被超时任务取消）→ 刷新为超时/已取消状态
      if (res.message && (res.message.includes('状态') || res.message.includes('取消'))) {
        onExpired()
      }
      loadOrder()
    }
  } finally {
    paying.value = false
  }
}

async function onCancel() {
  if (expired.value || paying.value || cancelling.value) return
  uni.showModal({
    title: '取消支付',
    content: '确定要取消该订单吗？',
    success: async (res) => {
      if (!res.confirm) return
      cancelling.value = true
      try {
        const result = await cancelOrder(orderNo.value, '用户主动取消')
        if (result.code === 0) {
          uni.showToast({ title: '订单已取消', icon: 'success' })
          setTimeout(() => {
            uni.redirectTo({ url: '/pages/order/list/list' })
          }, 600)
        } else {
          uni.showToast({ title: result.message || '取消失败', icon: 'none' })
          loadOrder()
        }
      } finally {
        cancelling.value = false
      }
    }
  })
}
</script>

<style scoped>
.pay-page {
  min-height: 100vh;
  background: #F4F6F5;
  padding-bottom: 140rpx;
  box-sizing: border-box;
}

.status-card {
  background: #FFFFFF;
  margin: 24rpx;
  padding: 48rpx 32rpx;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.status-card.expired {
  background: #FAFAFA;
}

.clock-icon-wrap {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background: #FFF7ED;
  border: 6rpx solid rgba(161, 98, 7, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28rpx;
}

.clock-icon-wrap.expired {
  background: #EEF0EE;
  border-color: rgba(107, 114, 128, 0.2);
}

/* 纯 CSS 时钟（跨端安全，不依赖 svg） */
.clock {
  position: relative;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  border: 6rpx solid #A16207;
  background: #FFFDF7;
  box-sizing: border-box;
}

.clock.expired {
  border-color: #9CA3AF;
  background: #F3F4F6;
}

.clock-hand {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 6rpx;
  border-radius: 3rpx;
}

.clock-hand.hour {
  height: 26rpx;
  background: #A16207;
  transform: translate(-50%, -100%) rotate(45deg);
}

.clock-hand.minute {
  height: 36rpx;
  background: #A16207;
  transform: translate(-50%, -100%) rotate(135deg);
}

.clock.expired .clock-hand {
  background: #9CA3AF;
}

.clock-dot {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #A16207;
  transform: translate(-50%, -50%);
}

.clock.expired .clock-dot {
  background: #9CA3AF;
}

.status-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 16rpx;
}

.status-card.expired .status-title {
  color: #6B7280;
}

.pay-amount {
  font-size: 64rpx;
  color: #A16207;
  font-weight: 600;
  margin-bottom: 24rpx;
  font-variant-numeric: tabular-nums;
}

.countdown {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.countdown-label {
  font-size: 26rpx;
  color: #6B7280;
}

.countdown-time {
  font-size: 44rpx;
  color: #C43436;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.expired-hint {
  font-size: 26rpx;
  color: #6B7280;
  margin-bottom: 16rpx;
  text-align: center;
}

.order-no {
  font-size: 24rpx;
  color: #9CA3AF;
}

.tips-card {
  background: #FFFFFF;
  margin: 0 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
}

.tip-row {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.tip-row:last-child { margin-bottom: 0; }

.tip-dot {
  color: #15803D;
  font-size: 28rpx;
  line-height: 1.5;
}

.tip-text {
  font-size: 26rpx;
  color: #6B7280;
  line-height: 1.5;
  flex: 1;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-height: 120rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #E3E7E5;
  z-index: 100;
}

.btn-cancel, .btn-pay {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
  text-align: center;
  box-sizing: border-box;
}

.btn-cancel:active:not(.disabled), .btn-pay:active:not(.disabled) {
  transform: scale(0.97);
  opacity: 0.9;
}

.btn-cancel {
  background: #FFFFFF;
  color: #4B5563;
  border: 1rpx solid #D1D5DB;
}

.btn-pay {
  background: #15803D;
  color: #FFFFFF;
  border: 1rpx solid #15803D;
}

.btn-cancel.disabled, .btn-pay.disabled {
  opacity: 0.5;
}
</style>
