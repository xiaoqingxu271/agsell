<template>
  <view class="seckill-page">
    <!-- 导航栏 -->
    <NavBar title="限时秒杀" @back="onBack" />

    <!-- 状态 Tab -->
    <view class="tab-bar">
      <view
        class="tab-item"
        :class="{ active: activeTab === 1 }"
        @click="switchTab(1)"
        role="button"
        :aria-selected="activeTab === 1"
      >
        <text>抢购中</text>
      </view>
      <view
        class="tab-item"
        :class="{ active: activeTab === 2 }"
        @click="switchTab(2)"
        role="button"
        :aria-selected="activeTab === 2"
      >
        <text>即将开始</text>
      </view>
    </view>

    <!-- 活动列表 -->
    <view v-if="loading" class="empty-box">
      <text class="empty-text">加载中...</text>
    </view>
    <view v-else-if="list.length === 0" class="empty-box">
      <text class="empty-text">{{ activeTab === 1 ? '暂无抢购中的秒杀活动' : '暂无即将开始的秒杀活动' }}</text>
    </view>
    <view v-else class="act-list">
      <view
        v-for="act in list"
        :key="act.id"
        class="act-card"
        @click="onActTap(act)"
        role="button"
        :aria-label="act.productName"
      >
        <image class="act-img" :src="act.productImage" mode="aspectFill" lazy-load :alt="act.productName" />
        <view class="act-info">
          <text class="act-name">{{ act.productName }}</text>
          <view class="act-price-row">
            <view class="act-price-box">
              <text class="act-price-label">秒杀价</text>
              <text class="act-price">¥{{ act.seckillPrice }}</text>
            </view>
            <text class="act-origin">¥{{ act.productPrice }}</text>
          </view>
          <view class="act-stock-row">
            <text class="act-stock-text">已抢 {{ act.progress }}%</text>
            <view class="act-progress">
              <view class="act-progress-bar" :style="{ width: (act.progress || 0) + '%' }"></view>
            </view>
          </view>
        </view>
        <view class="act-right">
          <view class="act-countdown" :class="{ 'act-countdown-warn': act.activityStatus === 2 }">
            <text class="cd-label">{{ act.activityStatus === 1 ? '距开始' : '距结束' }}</text>
            <text class="cd-value">{{ formatCountdown(act.countdownSeconds) }}</text>
          </view>
          <view class="act-btn" :class="{ 'act-btn-disabled': act.activityStatus !== 2 }">
            {{ act.activityStatus === 1 ? '即将开始' : '立即抢购' }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getSeckillList } from '../../api/seckill'

const activeTab = ref(1)
const list = ref([])
const loading = ref(false)
let timer = null

onMounted(() => {
  fetchList()
  // 每 1 秒刷新倒计时显示（本地递减，不重复请求）
  timer = setInterval(() => {
    list.value.forEach((act) => {
      if (act.countdownSeconds > 0) act.countdownSeconds -= 1
    })
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getSeckillList({ filter: activeTab.value === 1 ? 1 : 0 })
    if (res.code === 0) {
      list.value = (res.data || []).filter((a) =>
        activeTab.value === 1 ? a.activityStatus === 2 : a.activityStatus === 1
      )
    }
  } finally {
    loading.value = false
  }
}

function switchTab(tab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  fetchList()
}

function onActTap(act) {
  uni.navigateTo({ url: `/pages/seckill/detail?code=${act.activityCode}` })
}

function onBack() {
  uni.navigateBack({
    fail: () => uni.switchTab({ url: '/pages/index/index' })
  })
}

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

function formatCountdown(seconds) {
  const s = Math.max(0, Number(seconds) || 0)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return `${pad(h)}:${pad(m)}:${pad(sec)}`
}
</script>

<style scoped>
.seckill-page {
  min-height: 100vh;
  background: #F4F6F5;
  padding-bottom: 32rpx;
}

.tab-bar {
  display: flex;
  background: #FFFFFF;
  padding: 0 24rpx;
  border-bottom: 1rpx solid #E3E7E5;
}

.tab-item {
  padding: 24rpx 8rpx;
  margin-right: 48rpx;
  font-size: 30rpx;
  color: #6B7280;
  position: relative;
}

.tab-item.active {
  color: #FF4D2E;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 6rpx;
  border-radius: 3rpx;
  background: #FF4D2E;
}

.empty-box {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}

.empty-text {
  font-size: 26rpx;
  color: #9CA3AF;
}

.act-list {
  padding: 24rpx;
}

.act-card {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 20rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.act-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  background: #F4F6F5;
  flex-shrink: 0;
}

.act-info {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.act-name {
  font-size: 28rpx;
  color: #1F2937;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.act-price-row {
  display: flex;
  align-items: baseline;
  gap: 10rpx;
}

.act-price-box {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.act-price-label {
  font-size: 20rpx;
  color: #9CA3AF;
}

.act-price {
  font-size: 34rpx;
  font-weight: 700;
  color: #DC2626;
}

.act-origin {
  font-size: 22rpx;
  color: #9CA3AF;
  text-decoration: line-through;
}

.act-stock-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.act-stock-text {
  font-size: 20rpx;
  color: #9CA3AF;
  flex-shrink: 0;
}

.act-progress {
  flex: 1;
  height: 10rpx;
  background: #F3F4F6;
  border-radius: 5rpx;
  overflow: hidden;
}

.act-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #FF4D2E, #FF7A1A);
  border-radius: 5rpx;
}

.act-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  margin-left: 12rpx;
  flex-shrink: 0;
}

.act-countdown {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cd-label {
  font-size: 20rpx;
  color: #9CA3AF;
}

.cd-value {
  font-size: 26rpx;
  font-weight: 600;
  color: #374151;
  font-variant-numeric: tabular-nums;
}

.act-countdown-warn .cd-value {
  color: #FF4D2E;
}

.act-btn {
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 28rpx;
  background: #FF4D2E;
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 600;
  border-radius: 28rpx;
}

.act-btn-disabled {
  background: #D1D5DB;
}
</style>
