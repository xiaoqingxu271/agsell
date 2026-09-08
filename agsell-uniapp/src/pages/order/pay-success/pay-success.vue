<template>
  <view class="pay-success-page">
    <view class="success-content">
      <view class="success-icon-wrap">
        <svg class="success-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
          <polyline points="22 4 12 14.01 9 11.01"></polyline>
        </svg>
      </view>
      <text class="success-title">支付成功</text>
      <text class="success-amount">¥{{ payAmount }}</text>
      <text class="success-hint">订单已提交，请耐心等待发货</text>

      <view class="action-buttons">
        <view class="btn-order" @click="goToOrder" role="button">查看订单</view>
        <view class="btn-home" @click="goHome" role="button">返回首页</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createPayment } from '../../../api/payment'
import { getOrderDetail } from '../../../api/order'

const orderNo = ref('')
const payAmount = ref('0.00')

onLoad(async (options) => {
  orderNo.value = options.orderNo || ''
  payAmount.value = options.payAmount || '0.00'
  await handlePayment()
})

async function handlePayment() {
  if (!orderNo.value) return
  const res = await createPayment(orderNo.value)
  if (res.code !== 0) {
    console.error('支付失败', res)
  }
}

function goToOrder() {
  uni.redirectTo({ url: `/pages/order/detail/detail?orderNo=${orderNo.value}` })
}

function goHome() {
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style scoped>
.pay-success-page {
  min-height: 100vh;
  background: #F0FDF4;
  display: flex;
  align-items: center;
  justify-content: center;
}

.success-content {
  text-align: center;
  padding: 80rpx 40rpx;
}

.success-icon-wrap {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: #DCFCE7;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 40rpx;
}

.success-icon {
  width: 88rpx;
  height: 88rpx;
  color: #16A34A;
}

.success-title {
  font-size: 40rpx;
  font-weight: 600;
  color: #14532D;
  display: block;
  margin-bottom: 20rpx;
}

.success-amount {
  font-size: 56rpx;
  color: #A16207;
  font-weight: 600;
  display: block;
  margin-bottom: 16rpx;
  font-variant-numeric: tabular-nums;
}

.success-amount::before { content: '¥'; font-size: 32rpx; }

.success-hint {
  font-size: 28rpx;
  color: #6B7280;
  display: block;
  margin-bottom: 60rpx;
}

.action-buttons {
  display: flex;
  gap: 24rpx;
  justify-content: center;
}

.btn-order, .btn-home {
  padding: 0 60rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
  font-weight: 500;
  min-width: 88rpx;
  text-align: center;
  box-sizing: border-box;
}

.btn-order {
  background: #15803D;
  color: #FFFFFF;
  border: 1px solid #15803D;
}

.btn-home {
  background: #FFFFFF;
  color: #15803D;
  border: 1px solid #15803D;
}
</style>
