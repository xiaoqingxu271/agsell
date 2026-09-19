<template>
  <view class="coupon-center">
    <NavBar title="领券中心" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="content">
      <view v-if="!loading && !list.length" class="empty">
        <text class="empty-text">优惠券抢光了</text>
      </view>

      <view v-for="c in list" :key="c.id" class="coupon-card" :class="{ disabled: c.received || isSoldOut(c) }">
        <view class="coupon-left">
          <text class="coupon-amount">{{ c.couponType === 2 ? (Number(c.discount) * 10).toFixed(1) + '折' : '¥' + fmt(c.amount) }}</text>
          <text class="coupon-threshold">{{ Number(c.threshold) > 0 ? `满${fmt(c.threshold)}可用` : '无门槛' }}</text>
        </view>
        <view class="coupon-mid">
          <text class="coupon-name">{{ c.couponName }}</text>
          <text class="coupon-info">领取后 {{ c.validDays }} 天内有效</text>
        </view>
        <view class="coupon-right">
          <view
            v-if="!c.received && !isSoldOut(c)"
            class="receive-btn"
            @click="onReceive(c)"
          >领取</view>
          <view v-else-if="c.received" class="receive-btn disabled-btn">已领取</view>
          <view v-else class="receive-btn disabled-btn">抢光了</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getCouponList, receiveCoupon } from '../../api/coupon'

const list = ref([])
const loading = ref(false)

onLoad(async () => {
  await fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getCouponList()
    if (res.code === 0) {
      list.value = res.data || []
    }
  } catch (e) {
    uni.showToast({ title: '加载失败，请重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function isSoldOut(c) {
  return c.totalCount > 0 && c.receivedCount >= c.totalCount
}

function fmt(v) {
  return Number(v).toString()
}

async function onReceive(c) {
  try {
    const res = await receiveCoupon(c.id)
    if (res.code === 0) {
      uni.showToast({ title: '领取成功', icon: 'success' })
      await fetchList()
      uni.showModal({
        title: '领取成功',
        content: '可在「我的券包」查看使用',
        confirmText: '去查看',
        cancelText: '继续逛',
        success: (r) => {
          if (r.confirm) {
            uni.navigateTo({ url: '/pages/coupon/my' })
          }
        }
      })
    } else {
      uni.showToast({ title: res.message || '领取失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: '领取失败，请重试', icon: 'none' })
  }
}
</script>

<style scoped>
.coupon-center {
  min-height: 100vh;
  background: #F5F5F5;
}

.content {
  height: calc(100vh - 100rpx);
  padding: 24rpx;
  box-sizing: border-box;
}

.empty {
  padding: 200rpx 0;
  text-align: center;
}

.empty-text {
  color: #9CA3AF;
  font-size: 28rpx;
}

.coupon-card {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  border-radius: 24rpx;
  margin-bottom: 24rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 8rpx rgba(16, 24, 40, 0.06);
}

.coupon-card.disabled {
  opacity: 0.55;
}

.coupon-left {
  width: 200rpx;
  padding: 32rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #FEF3E2, #FDE8C8);
  position: relative;
}

.coupon-left::after {
  content: '';
  position: absolute;
  right: -14rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 28rpx;
  height: 28rpx;
  background: #F5F5F5;
  border-radius: 50%;
}

.coupon-amount {
  font-size: 44rpx;
  font-weight: 700;
  color: #B45309;
}

.coupon-threshold {
  font-size: 20rpx;
  color: #9A6A2F;
  margin-top: 6rpx;
}

.coupon-mid {
  flex: 1;
  padding: 24rpx 28rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  overflow: hidden;
}

.coupon-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.coupon-info,
.coupon-stock {
  font-size: 22rpx;
  color: #9CA3AF;
}

.coupon-right {
  padding-right: 28rpx;
}

.receive-btn {
  background: #B45309;
  color: #FFFFFF;
  font-size: 26rpx;
  font-weight: 600;
  padding: 14rpx 36rpx;
  border-radius: 36rpx;
}

.receive-btn:active {
  opacity: 0.85;
}

.disabled-btn {
  background: #D1D5DB;
  color: #6B7280;
}
</style>
