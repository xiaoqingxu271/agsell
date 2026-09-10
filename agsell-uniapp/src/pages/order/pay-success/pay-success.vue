<template>
  <view class="pay-success-page">
    <view class="success-content">
      <view class="success-icon-wrap">
        <view class="checkmark"></view>
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

const orderNo = ref('')
const payAmount = ref('0.00')

onLoad((options) => {
  orderNo.value = options.orderNo || ''
  payAmount.value = options.payAmount || '0.00'
})

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
  background: #F4F6F5;
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
  border: 6rpx solid rgba(22, 163, 74, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 40rpx;
}

.checkmark {
  width: 46rpx;
  height: 24rpx;
  border-left: 8rpx solid #16A34A;
  border-bottom: 8rpx solid #16A34A;
  border-radius: 2rpx;
  transform: rotate(-45deg);
  margin-top: -6rpx;
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
  transition: transform 120ms ease-out, opacity 120ms ease-out;
}

.btn-order:active, .btn-home:active {
  transform: scale(0.96);
  opacity: 0.9;
}

.btn-order {
  background: #15803D;
  color: #FFFFFF;
  border: 1rpx solid #15803D;
}

.btn-home {
  background: #FFFFFF;
  color: #15803D;
  border: 1rpx solid #15803D;
}
</style>
