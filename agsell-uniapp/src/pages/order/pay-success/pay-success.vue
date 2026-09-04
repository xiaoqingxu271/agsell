<template>
  <view class="pay-success-page">
    <view class="success-content">
      <view class="success-icon">✓</view>
      <text class="success-title">支付成功</text>
      <text class="success-amount">¥{{ payAmount }}</text>
      <text class="success-hint">订单已提交，请耐心等待发货</text>

      <view class="action-buttons">
        <view class="btn-order" @click="goToOrder">查看订单</view>
        <view class="btn-home" @click="goHome">返回首页</view>
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
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.success-content {
  text-align: center;
  padding: 80rpx 40rpx;
}

.success-icon {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: #4CAF50;
  color: #fff;
  font-size: 80rpx;
  line-height: 160rpx;
  margin: 0 auto 40rpx;
}

.success-title {
  font-size: 40rpx;
  font-weight: bold;
  color: #333;
  display: block;
  margin-bottom: 20rpx;
}

.success-amount {
  font-size: 56rpx;
  color: #FF9800;
  font-weight: bold;
  display: block;
  margin-bottom: 16rpx;
}

.success-amount::before { content: '¥'; font-size: 32rpx; }

.success-hint {
  font-size: 28rpx;
  color: #999;
  display: block;
  margin-bottom: 60rpx;
}

.action-buttons {
  display: flex;
  gap: 24rpx;
  justify-content: center;
}

.btn-order, .btn-home {
  padding: 24rpx 60rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
}

.btn-order {
  background: #4CAF50;
  color: #fff;
}

.btn-home {
  background: #fff;
  color: #666;
  border: 1rpx solid #ddd;
}
</style>
