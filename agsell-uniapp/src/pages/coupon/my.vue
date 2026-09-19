<template>
  <view class="coupon-my">
    <NavBar title="我的券包" @back="uni.navigateBack()" />

    <!-- Tab -->
    <view class="tab-bar">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="tab-item"
        :class="{ active: activeTab === tab.value }"
        @click="switchTab(tab.value)"
      >
        <text>{{ tab.label }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="content" @scrolltolower="loadMore">
      <view v-if="!loading && !list.length" class="empty">
        <text class="empty-text">{{ activeTab === 0 ? '还没有可用优惠券，去领券中心看看吧' : '暂无相关记录' }}</text>
        <view v-if="activeTab === 0" class="empty-btn" @click="goCenter">去领券</view>
      </view>

      <view v-for="c in list" :key="c.id" class="coupon-card" :class="'status-' + c.status">
        <view class="coupon-left">
          <text class="coupon-amount">{{ c.couponType === 2 ? (Number(c.discount) * 10).toFixed(1) + '折' : '¥' + fmt(c.amount) }}</text>
          <text class="coupon-threshold">{{ Number(c.threshold) > 0 ? `满${fmt(c.threshold)}可用` : '无门槛' }}</text>
        </view>
        <view class="coupon-mid">
          <text class="coupon-name">{{ c.couponName }}</text>
          <text class="coupon-info">
            {{ c.status === 0 ? formatExpire(c.expireTime) : (c.status === 1 ? `已使用${c.useTime ? ' · ' + fmtTime(c.useTime) : ''}` : '已过期') }}
          </text>
        </view>
        <view class="coupon-right">
          <view v-if="c.status === 0" class="use-btn" @click="goUse">去使用</view>
          <view v-else class="use-btn done">{{ c.status === 1 ? '已使用' : '已过期' }}</view>
        </view>
      </view>

      <view v-if="hasMore" class="load-more">上拉加载更多</view>
      <view v-if="!hasMore && list.length" class="load-more no-more">没有更多了</view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getMyCoupons } from '../../api/coupon'

const tabs = [
  { label: '未使用', value: 0 },
  { label: '已使用', value: 1 },
  { label: '已过期', value: 2 }
]
const activeTab = ref(0)
const list = ref([])
const page = ref(1)
const pageSize = 10
const hasMore = ref(true)
const loading = ref(false)

onLoad(() => {
  fetchList(true)
})

onShow(() => {
  fetchList(true)
})

function switchTab(val) {
  if (activeTab.value === val) return
  activeTab.value = val
  fetchList(true)
}

async function fetchList(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    hasMore.value = true
  }
  loading.value = true
  try {
    const res = await getMyCoupons({ pageNum: page.value, pageSize, status: activeTab.value })
    if (res.code === 0) {
      const records = res.data?.records || []
      list.value = reset ? records : [...list.value, ...records]
      hasMore.value = page.value * pageSize < Number(res.data?.total || 0)
      if (records.length) page.value += 1
    }
  } catch (e) {
    uni.showToast({ title: '加载失败，请重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (hasMore.value) fetchList()
}

function fmt(v) {
  return Number(v).toString()
}

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

function formatExpire(t) {
  if (!t) return ''
  const d = new Date(t.replace('T', ' ').replace(/-/g, '/'))
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} 到期`
}

function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t.replace('T', ' ').replace(/-/g, '/'))
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function goCenter() {
  uni.navigateTo({ url: '/pages/coupon/center' })
}

function goUse() {
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style scoped>
.coupon-my {
  min-height: 100vh;
  background: #F5F5F5;
}

.tab-bar {
  display: flex;
  background: #FFFFFF;
  border-bottom: 1rpx solid #E5E7EB;
  position: sticky;
  top: 0;
  z-index: 10;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 28rpx;
  color: #6B7280;
  position: relative;
}

.tab-item.active {
  color: #00B578;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background: #00B578;
}

.content {
  height: calc(100vh - 200rpx);
  padding: 24rpx;
  box-sizing: border-box;
}

.empty {
  padding: 160rpx 0;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
}

.empty-text {
  color: #9CA3AF;
  font-size: 28rpx;
}

.empty-btn {
  background: #00B578;
  color: #FFFFFF;
  font-size: 26rpx;
  padding: 14rpx 48rpx;
  border-radius: 40rpx;
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

.coupon-card.status-1,
.coupon-card.status-2 {
  opacity: 0.6;
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

.coupon-info {
  font-size: 22rpx;
  color: #9CA3AF;
}

.coupon-right {
  padding-right: 28rpx;
}

.use-btn {
  background: #00B578;
  color: #FFFFFF;
  font-size: 26rpx;
  font-weight: 600;
  padding: 14rpx 36rpx;
  border-radius: 36rpx;
}

.use-btn:active {
  opacity: 0.85;
}

.use-btn.done {
  background: #E5E7EB;
  color: #9CA3AF;
}

.load-more {
  text-align: center;
  color: #9CA3AF;
  font-size: 24rpx;
  padding: 24rpx 0;
}

.load-more.no-more {
  color: #D1D5DB;
}
</style>
