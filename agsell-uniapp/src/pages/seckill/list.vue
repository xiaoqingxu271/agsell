<template>
  <view class="seckill-page">
    <!-- 红底频道头导航 -->
    <NavBar title="限时秒杀" background="#E1251B" color="#FFFFFF" :bordered="false" @back="onBack" />

    <!-- 频道头：本场倒计时 + 场次切换（红底） -->
    <view class="sk-header">
      <view v-if="countdownSegments.length" class="sk-cd">
        <text class="sk-cd-label">{{ activeTab === 1 ? '本场剩余' : '距开始' }}</text>
        <template v-for="(seg, i) in countdownSegments" :key="i">
          <text v-if="i > 0" class="sk-cd-sep">:</text>
          <text class="sk-cd-num">{{ seg }}</text>
        </template>
      </view>
      <view class="sessions">
        <view
          class="session"
          :class="{ on: activeTab === 1, dis: activeList.length === 0 }"
          @click="switchTab(1)"
          role="button"
          :aria-selected="activeTab === 1"
        >
          <text class="session-tm">{{ sessionTime(1) }}</text>
          <text class="session-st">{{ activeTab === 1 && activeList.length === 0 ? '暂无场次' : '抢购中' }}</text>
        </view>
        <view
          class="session"
          :class="{ on: activeTab === 2, dis: upcomingList.length === 0 }"
          @click="switchTab(2)"
          role="button"
          :aria-selected="activeTab === 2"
        >
          <text class="session-tm">{{ sessionTime(2) }}</text>
          <text class="session-st">{{ upcomingList.length === 0 ? '暂无场次' : '即将开始' }}</text>
        </view>
      </view>
    </view>

    <!-- 活动列表 -->
    <view v-if="loading" class="empty-box">
      <text class="empty-text">加载中...</text>
    </view>
    <view v-else-if="currentList.length === 0" class="empty-box">
      <text class="empty-text">{{ activeTab === 1 ? '暂无抢购中的秒杀活动' : '暂无即将开始的秒杀活动' }}</text>
    </view>
    <view v-else class="sflow">
      <view
        v-for="act in currentList"
        :key="act.id"
        class="scard"
        :class="{ lock: isUpcoming(act), 'sold-out': isSoldOut(act) }"
        @click="onActTap(act)"
        role="button"
        :aria-label="act.productName"
      >
        <view class="scard-thumb">
          <image
            v-if="act.productImage"
            class="scard-img"
            :src="act.productImage"
            mode="aspectFill"
            lazy-load
            :alt="act.productName"
          />
          <wd-icon v-else name="picture" size="116rpx" custom-class="scard-ph-ic" />
          <text v-if="!isSoldOut(act)" class="scard-flag">{{ dropLabel(act) }}</text>
          <text v-if="isSoldOut(act)" class="sold-out-tag">已抢光</text>
        </view>
        <view class="scard-in">
          <text class="scard-name">{{ act.productName }}</text>
          <view class="scard-price">
            <text class="price-cur">¥</text>
            <text class="price-int">{{ act.seckillPrice }}</text>
            <text class="price-origin">{{ act.productPrice }}</text>
          </view>
          <view class="scard-bar" :class="{ 'scard-bar--idle': isUpcoming(act) || isSoldOut(act) }">
            <view
              v-if="!isUpcoming(act) && !isSoldOut(act)"
              class="bar-fill"
              :style="{ width: (act.progress || 0) + '%' }"
            ></view>
            <text class="bar-text">{{ barLabel(act) }}</text>
          </view>
        </view>
        <view class="scard-go">
          <wd-icon v-if="isUpcoming(act)" name="clock" size="24rpx" custom-class="go-icon" />
          <wd-icon v-else-if="!isSoldOut(act)" name="discount" size="24rpx" custom-class="go-icon" />
          <text class="go-text">{{ goLabel(act) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getSeckillList } from '../../api/seckill'

const activeTab = ref(1)
const allActs = ref([])
const loading = ref(false)
let timer = null

const activeList = computed(() => allActs.value.filter(a => a.activityStatus === 2))
const upcomingList = computed(() => allActs.value.filter(a => a.activityStatus === 1))
const currentList = computed(() => (activeTab.value === 1 ? activeList.value : upcomingList.value))

/** 头部倒计时：取当前场次首个活动 */
const headAct = computed(() => currentList.value[0] || null)
const countdownSegments = computed(() => {
  if (!headAct.value) return []
  const s = Math.max(0, Number(headAct.value.countdownSeconds) || 0)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return [pad(h), pad(m), pad(sec)]
})

onMounted(() => {
  fetchList()
  // 每 1 秒刷新倒计时显示（本地递减，不重复请求）
  timer = setInterval(() => {
    allActs.value.forEach((act) => {
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
    const res = await getSeckillList({ filter: 0 })
    if (res.code === 0) {
      // 未开始 + 进行中一次拉齐，场次切换本地过滤
      allActs.value = (res.data || []).filter(a => a.activityStatus === 1 || a.activityStatus === 2)
    }
  } finally {
    loading.value = false
  }
}

/** 下拉刷新：重新取库存进度与倒计时基准 */
onPullDownRefresh(async () => {
  try {
    await fetchList()
  } catch (e) {
    console.warn('[Seckill] 下拉刷新失败', e)
  } finally {
    uni.stopPullDownRefresh()
  }
})

function switchTab(tab) {
  activeTab.value = tab
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

/** 场次时间标签：取该场次首个活动的开始时间 HH:mm */
function sessionTime(tab) {
  const act = (tab === 1 ? activeList.value : upcomingList.value)[0]
  return act ? hhmm(act.startTime) : '--:--'
}

function hhmm(v) {
  const s = String(v || '').replace('T', ' ')
  return /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(s) ? s.slice(11, 16) : '--:--'
}

function isUpcoming(act) {
  return act.activityStatus === 1
}

function isSoldOut(act) {
  return act.activityStatus === 2 && (act.remainingStock === 0 || (act.progress || 0) >= 100)
}

function dropLabel(act) {
  const diff = Number(act.productPrice) - Number(act.seckillPrice)
  if (!Number.isFinite(diff) || diff <= 0) return '秒杀'
  return '直降' + (Number.isInteger(diff) ? diff : diff.toFixed(1))
}

function barLabel(act) {
  if (isSoldOut(act)) return '已抢光'
  if (isUpcoming(act)) return hhmm(act.startTime) === '--:--' ? '未开始' : hhmm(act.startTime) + ' 开抢'
  return `已抢 ${act.progress || 0}%`
}

function goLabel(act) {
  if (isSoldOut(act)) return '看相似'
  return isUpcoming(act) ? '提醒我' : '马上抢'
}
</script>

<style lang="scss" scoped>
.seckill-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
  padding-bottom: 32rpx;
}

/* ── 红底频道头 + 场次 Tab ── */
.sk-header {
  background: $uni-color-primary;
  padding: 20rpx 24rpx 0;
}

.sk-cd {
  display: flex;
  align-items: center;
  gap: 10rpx;
  font-size: 22rpx;
  color: $uni-text-color-inverse;
  opacity: 0.92;
}

.sk-cd-label {
  margin-right: 6rpx;
}

.sk-cd-sep {
  color: $uni-text-color-inverse;
  font-weight: 600;
}

.sk-cd-num {
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  background: rgba(0, 0, 0, 0.28);
  color: $uni-text-color-inverse;
  font-size: 24rpx;
  font-weight: 600;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
  min-width: 36rpx;
  text-align: center;
}

.sessions {
  display: flex;
  gap: 4rpx;
  padding: 20rpx 12rpx 0;
}

.session {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  position: relative;
  border-radius: $uni-border-radius-base $uni-border-radius-base 0 0;
}

.session-tm {
  display: block;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1;
  color: rgba(255, 255, 255, 0.7);
}

.session-st {
  display: block;
  margin-top: 6rpx;
  font-size: 18rpx;
  color: rgba(255, 255, 255, 0.6);
}

.session.dis .session-tm {
  color: rgba(255, 255, 255, 0.38);
}

.session.on {
  background: $uni-bg-color-grey;
}

.session.on .session-tm,
.session.on .session-st {
  color: $uni-color-primary;
}

.session.on::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -10rpx;
  transform: translateX(-50%);
  border: 10rpx solid transparent;
  border-bottom-color: $uni-bg-color-grey;
}

/* ── 空状态 ── */
.empty-box {
  padding: 160rpx 0;
  display: flex;
  justify-content: center;
}

.empty-text {
  font-size: 26rpx;
  color: $uni-text-color-placeholder;
}

/* ── 双列定高卡片流 ── */
.sflow {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  padding: 20rpx 24rpx 32rpx;
}

.scard {
  background: $uni-bg-color;
  border-radius: $uni-border-radius-base;
  overflow: hidden;
  box-shadow: $uni-shadow-sm;
  padding-bottom: 18rpx;
  display: flex;
  flex-direction: column;
}

.scard.lock {
  opacity: 0.6;
}

.scard.sold-out .scard-thumb {
  filter: grayscale(1);
}

.scard.sold-out .scard-go {
  background: $uni-bg-color-grey;
}

.scard.sold-out .go-text {
  color: $uni-text-color-placeholder;
}

.scard-thumb {
  position: relative;
  width: 100%;
  height: 260rpx;
  background: $uni-bg-color-img;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scard-img {
  width: 100%;
  height: 260rpx;
}

:deep(.scard-ph-ic) {
  color: $uni-color-img-icon;
}

.scard-flag {
  position: absolute;
  top: 0;
  left: 0;
  background: $uni-color-primary;
  color: $uni-text-color-inverse;
  font-size: 18rpx;
  font-weight: 600;
  padding: 4rpx 10rpx;
  border-radius: 8rpx 0 8rpx 0;
}

.sold-out-tag {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  color: $uni-text-color-grey;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.scard-in {
  padding: 16rpx 18rpx 0;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.scard-name {
  font-size: 24rpx;
  line-height: 34rpx;
  height: 68rpx;
  color: $uni-text-color;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.scard-price {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-top: 12rpx;
}

.price-cur {
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  font-size: 20rpx;
  font-weight: 700;
  color: $uni-color-price;
}

.price-int {
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  font-size: 38rpx;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -1rpx;
  color: $uni-color-price;
}

.price-origin {
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
  text-decoration: line-through;
}

/* 进度条 */
.scard-bar {
  position: relative;
  height: 28rpx;
  margin-top: 14rpx;
  border-radius: 14rpx;
  background: $uni-color-primary-50;
  overflow: hidden;
}

.scard-bar--idle {
  background: $uni-bg-color-img;
}

.bar-fill {
  height: 100%;
  border-radius: 14rpx;
  background: linear-gradient(90deg, $uni-color-price-light, $uni-color-price);
}

.bar-text {
  position: absolute;
  left: 12rpx;
  top: 0;
  height: 28rpx;
  line-height: 28rpx;
  font-size: 18rpx;
  font-weight: 600;
  color: $uni-text-color-inverse;
}

.scard-bar--idle .bar-text {
  color: $uni-text-color-placeholder;
  font-weight: 400;
}

/* CTA 胶囊 */
.scard-go {
  margin: 18rpx 18rpx 0;
  height: 60rpx;
  border-radius: 30rpx;
  background: $uni-color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
}

.scard.lock .scard-go {
  background: $uni-bg-color;
  border: 2rpx solid $uni-color-primary;
}

:deep(.go-icon) {
  color: $uni-text-color-inverse;
}

.scard.lock :deep(.go-icon) {
  color: $uni-color-primary;
}

.go-text {
  font-size: 24rpx;
  font-weight: 600;
  color: $uni-text-color-inverse;
}

.scard.lock .go-text {
  color: $uni-color-primary;
}
</style>
