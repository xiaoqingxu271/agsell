<template>
  <view class="detail-page">
    <!-- 导航栏 -->
    <NavBar title="秒杀详情" @back="onBack" />

    <!-- 商品大图 -->
    <view class="hero">
      <image class="hero-img" :src="currentSpec?.specImage || detail?.productImage" mode="aspectFill" :alt="detail?.productName" />
      <view v-if="currentSpec && currentSpec.activityStatus !== 2" class="hero-status-mask">
        <text class="hero-status-text">{{ currentSpec.activityStatus === 1 ? '未开始' : '已结束' }}</text>
      </view>
    </view>

    <!-- 价格与活动信息（绑定选中规格） -->
    <view class="price-card" v-if="currentSpec">
      <view class="price-row">
        <view class="price-box">
          <text class="price-currency">¥</text>
          <text class="price-value">{{ currentSpec.seckillPrice }}</text>
          <text class="price-origin">¥{{ detail.productPrice }}</text>
        </view>
        <view class="countdown-box" :class="{ 'countdown-ongoing': currentSpec.activityStatus === 2 }">
          <text class="cd-label">{{ currentSpec.activityStatus === 1 ? '距开始' : currentSpec.activityStatus === 2 ? '距结束' : '活动已结束' }}</text>
          <text v-if="currentSpec.activityStatus !== 3" class="cd-value">{{ countdownText }}</text>
        </view>
      </view>
      <view class="stock-row">
        <view class="stock-left">
          <text class="stock-text">已抢 {{ currentSpec.progress || 0 }}%</text>
          <view class="stock-progress">
            <view class="stock-progress-bar" :style="{ width: (currentSpec.progress || 0) + '%' }"></view>
          </view>
        </view>
        <text class="stock-remain">剩余 {{ currentSpec.remainingStock ?? 0 }} 件</text>
      </view>
    </view>

    <!-- 规格选择（多规格时显示） -->
    <view class="spec-card" v-if="detail && detail.specs && detail.specs.length > 1">
      <view class="spec-title">选择规格</view>
      <view class="spec-list">
        <view
          v-for="spec in detail.specs"
          :key="spec.activityCode"
          class="spec-item"
          :class="{ active: selectedSpecCode === spec.activityCode, 'spec-item-disabled': spec.activityStatus === 3 }"
          @click="onSpecTap(spec)"
          role="button"
        >
          <text class="spec-name">{{ spec.specName }}</text>
          <text class="spec-price">¥{{ spec.seckillPrice }}</text>
        </view>
      </view>
    </view>

    <!-- 商品信息 -->
    <view class="info-card" v-if="detail">
      <text class="info-name">{{ detail.productName }}</text>
      <view class="info-grid">
        <view class="info-item">
          <text class="info-label">商品产地</text>
          <text class="info-value">{{ detail.origin || '-' }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">保质期</text>
          <text class="info-value">{{ detail.shelfLife || '-' }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">储存方式</text>
          <text class="info-value">{{ detail.storage || '-' }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">每人限购</text>
          <text class="info-value">{{ currentSpec?.seckillLimit || 1 }} 件</text>
        </view>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="bottom-left">
        <text class="bottom-price">¥{{ currentSpec?.seckillPrice }}</text>
        <text class="bottom-origin">¥{{ detail?.productPrice }}</text>
      </view>
      <view
        class="buy-btn"
        :class="{ 'buy-btn-disabled': !canBuy }"
        @click="onBuy"
        role="button"
        :aria-label="buyText"
      >
        <text>{{ buyText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getSeckillDetail, createSeckillOrder } from '../../api/seckill'
import { getAddressList } from '../../api/user'
import { isLoggedIn, wxLogin } from '../../utils/request'

const activityCode = ref('')
const detail = ref(null)
const selectedSpecCode = ref('')
const buying = ref(false)
let timer = null

onLoad((options) => {
  activityCode.value = options.code || ''
  loadDetail()
})

onMounted(() => {
  timer = setInterval(() => {
    // 对所有规格倒计时递减
    if (detail.value && detail.value.specs) {
      detail.value.specs.forEach((s) => {
        if (s.countdownSeconds > 0) s.countdownSeconds -= 1
      })
    }
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function loadDetail() {
  if (!activityCode.value) {
    uni.showToast({ title: '活动编号无效', icon: 'none' })
    return
  }
  try {
    const res = await getSeckillDetail(activityCode.value)
    if (res.code === 0) {
      detail.value = res.data
      // 默认选中当前 activityCode 对应的规格
      selectedSpecCode.value = activityCode.value
    } else {
      uni.showToast({ title: res.message || '加载失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
  }
}

function onSpecTap(spec) {
  if (spec.activityStatus === 3) {
    uni.showToast({ title: '该规格已结束', icon: 'none' })
    return
  }
  selectedSpecCode.value = spec.activityCode
}

/** 当前选中的规格对象 */
const currentSpec = computed(() => {
  if (!detail.value || !detail.value.specs) return null
  return detail.value.specs.find((s) => s.activityCode === selectedSpecCode.value) || null
})

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

const countdownText = computed(() => {
  const s = Math.max(0, Number(currentSpec.value?.countdownSeconds) || 0)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return `${pad(h)}:${pad(m)}:${pad(sec)}`
})

const canBuy = computed(() => {
  if (!currentSpec.value) return false
  if (currentSpec.value.activityStatus !== 2) return false
  if (currentSpec.value.userSeckilled) return false
  return true
})

const buyText = computed(() => {
  if (!currentSpec.value) return '加载中'
  if (currentSpec.value.activityStatus === 1) return '即将开始'
  if (currentSpec.value.activityStatus === 3) return '已结束'
  if (currentSpec.value.userSeckilled) return '已参与过'
  return '立即抢购'
})

function onBack() {
  uni.navigateBack({
    fail: () => uni.switchTab({ url: '/pages/index/index' })
  })
}

async function pickDefaultAddress() {
  const res = await getAddressList()
  if (res.code !== 0) return null
  const list = res.data || []
  const def = list.find((a) => a.isDefault === 1) || list[0]
  return def || null
}

async function onBuy() {
  if (!canBuy.value || buying.value) return
  buying.value = true
  try {
    if (!isLoggedIn()) {
      const ok = await wxLogin()
      if (!ok) {
        uni.showToast({ title: '请先登录后再抢购', icon: 'none' })
        return
      }
    }
    const address = await pickDefaultAddress()
    if (!address) {
      uni.showToast({ title: '请先在「我的-收货地址」中添加地址', icon: 'none' })
      uni.navigateTo({ url: '/pages/mine/address' })
      return
    }
    // 使用选中规格对应的 activityCode 下单
    const res = await createSeckillOrder({
      activityCode: currentSpec.value.activityCode,
      addressId: address.id,
      remark: ''
    })
    if (res.code === 0 && res.data) {
      uni.redirectTo({
        url: `/pages/order/pay/pay?orderNo=${res.data.orderNo}&payAmount=${res.data.payAmount}`
      })
    } else {
      uni.showToast({ title: res.message || '抢购失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: '抢购失败，请重试', icon: 'none' })
  } finally {
    buying.value = false
  }
}
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: #F4F6F5;
  padding-bottom: 140rpx;
}

.hero {
  position: relative;
  width: 100%;
  height: 600rpx;
  background: #FFFFFF;
}

.hero-img {
  width: 100%;
  height: 600rpx;
}

.hero-status-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-status-text {
  color: #FFFFFF;
  font-size: 40rpx;
  font-weight: 700;
  letter-spacing: 8rpx;
}

.price-card {
  margin: 24rpx;
  padding: 28rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
}

.price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.price-box {
  display: flex;
  align-items: baseline;
}

.price-currency {
  font-size: 28rpx;
  color: #DC2626;
  font-weight: 700;
}

.price-value {
  font-size: 56rpx;
  font-weight: 800;
  color: #DC2626;
  line-height: 1;
  margin: 0 12rpx 0 4rpx;
}

.price-origin {
  font-size: 26rpx;
  color: #9CA3AF;
  text-decoration: line-through;
}

.countdown-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8rpx 20rpx;
  border-radius: 12rpx;
  background: #F3F4F6;
}

.countdown-ongoing {
  background: #FFF1ED;
}

.cd-label {
  font-size: 20rpx;
  color: #9CA3AF;
}

.cd-value {
  font-size: 28rpx;
  font-weight: 700;
  color: #FF4D2E;
  font-variant-numeric: tabular-nums;
}

.stock-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 24rpx;
}

.stock-left {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.stock-text {
  font-size: 22rpx;
  color: #9CA3AF;
  flex-shrink: 0;
}

.stock-progress {
  flex: 1;
  height: 12rpx;
  background: #F3F4F6;
  border-radius: 6rpx;
  overflow: hidden;
}

.stock-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #FF4D2E, #FF7A1A);
  border-radius: 6rpx;
}

.stock-remain {
  font-size: 22rpx;
  color: #FF4D2E;
  flex-shrink: 0;
}

/* 规格选择 */
.spec-card {
  margin: 0 24rpx 24rpx;
  padding: 24rpx 28rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
}

.spec-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 20rpx;
}

.spec-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.spec-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 24rpx;
  border: 1rpx solid #D1D5DB;
  border-radius: 16rpx;
  min-width: 180rpx;
  box-sizing: border-box;
  transition: border-color 150ms ease-out, background 150ms ease-out;
}

.spec-item.active {
  border-color: #FF4D2E;
  background: #FFF1ED;
}

.spec-item-disabled {
  opacity: 0.5;
}

.spec-name {
  font-size: 26rpx;
  font-weight: 600;
  color: #1F2937;
}

.spec-price {
  font-size: 24rpx;
  color: #DC2626;
  font-weight: 600;
  margin-top: 4rpx;
}

.info-card {
  margin: 0 24rpx;
  padding: 28rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
}

.info-name {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 24rpx;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24rpx;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.info-label {
  font-size: 22rpx;
  color: #9CA3AF;
}

.info-value {
  font-size: 26rpx;
  color: #1F2937;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 120rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -4rpx 16rpx rgba(16, 24, 40, 0.06);
}

.bottom-left {
  display: flex;
  align-items: baseline;
}

.bottom-price {
  font-size: 40rpx;
  font-weight: 800;
  color: #DC2626;
}

.bottom-origin {
  font-size: 24rpx;
  color: #9CA3AF;
  text-decoration: line-through;
  margin-left: 12rpx;
}

.buy-btn {
  height: 88rpx;
  line-height: 88rpx;
  padding: 0 64rpx;
  background: linear-gradient(135deg, #FF4D2E, #FF7A1A);
  color: #FFFFFF;
  font-size: 32rpx;
  font-weight: 600;
  border-radius: 44rpx;
}

.buy-btn-disabled {
  background: #D1D5DB;
}
</style>
