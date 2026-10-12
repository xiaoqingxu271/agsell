<template>
  <view class="mine-page">
    <!-- 头部：白底，不用大面积色块 -->
    <view v-if="isUserLoggedIn" class="mhd">
      <image v-if="userInfo?.avatar" class="mhd-av" :src="userInfo.avatar" mode="aspectFill" lazy-load @click="onChooseAvatar" />
      <view v-else class="mhd-av mhd-av--ph" @click="onChooseAvatar">
        <wd-icon name="user" size="52rpx" custom-class="mhd-av-ic" />
      </view>
      <view class="mhd-info" @click="goToProfile" role="button" aria-label="编辑资料">
        <view class="mhd-name-row">
          <text class="mhd-name">{{ userInfo?.nickname || '微信用户' }}</text>
          <wd-icon name="edit" size="26rpx" custom-class="mhd-edit-ic" />
        </view>
        <text class="mhd-sub">{{ phoneText }}</text>
      </view>
      <view class="mhd-qr" @click="onScanTrace" role="button" aria-label="扫码溯源">
        <wd-icon name="scan" size="34rpx" custom-class="mhd-qr-ic" />
        <text class="mhd-qr-t">扫码</text>
      </view>
      <view class="mhd-qr mhd-qr--gap" @click="goToSettings" role="button" aria-label="设置">
        <wd-icon name="setting" size="34rpx" custom-class="mhd-qr-ic" />
        <text class="mhd-qr-t">设置</text>
      </view>
    </view>
    <view v-else class="mhd login-prompt">
      <view class="mhd-av mhd-av--ph">
        <wd-icon name="user" size="52rpx" custom-class="mhd-av-ic" />
      </view>
      <view class="mhd-info">
        <text class="mhd-name">登录后享受更多权益</text>
        <text class="mhd-sub">订单管理 · 收货地址 · 商品评价</text>
      </view>
      <wd-button
        type="primary"
        custom-style="width: 208rpx; height: 64rpx; border-radius: 32rpx; font-weight: 600;"
        @click.stop="onLogin"
      >微信一键登录</wd-button>
    </view>

    <!-- 订单区白卡 -->
    <view class="card ord-card">
      <view class="ord-hd">
        <text class="ord-title">我的订单</text>
        <view class="ord-all" @click="goToAllOrders" role="button" aria-label="查看全部订单">
          <text class="ord-all-txt">全部订单</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="ord-all-ic" />
        </view>
      </view>
      <view class="ord-grid">
        <view
          v-for="(item, index) in orderTabs"
          :key="index"
          class="ord-tab"
          @click="onOrderTabTap(item.status)"
          role="button"
          :aria-label="item.label"
        >
          <wd-icon :name="item.icon" size="42rpx" custom-class="ord-ic" />
          <text class="ord-label">{{ item.label }}</text>
          <text v-if="item.count > 0" class="ord-cb">{{ item.count > 99 ? '99+' : item.count }}</text>
        </view>
        <view class="ord-tab" @click="goToAfterSales" role="button" aria-label="退款/售后">
          <wd-icon name="refresh" size="42rpx" custom-class="ord-ic" />
          <text class="ord-label">退款/售后</text>
          <text v-if="assetCounts.afterSales > 0" class="ord-cb">{{ assetCounts.afterSales > 99 ? '99+' : assetCounts.afterSales }}</text>
        </view>
      </view>
    </view>

    <!-- 资产四列 -->
    <view v-if="isUserLoggedIn" class="card ast-card">
      <view class="ast-col" @click="goToMyCoupons" role="button" aria-label="优惠券">
        <text class="ast-num">{{ assetCounts.coupon }}</text>
        <text class="ast-label">优惠券</text>
      </view>
      <view class="ast-col" @click="goToMyReviews" role="button" aria-label="我的评价">
        <text class="ast-num">{{ assetCounts.review }}</text>
        <text class="ast-label">我的评价</text>
      </view>
      <view class="ast-col" @click="goToAddress" role="button" aria-label="收货地址">
        <text class="ast-num">{{ assetCounts.address }}</text>
        <text class="ast-label">收货地址</text>
      </view>
      <view class="ast-col ast-col--last" @click="goToAfterSales" role="button" aria-label="退款售后">
        <text class="ast-num">{{ assetCounts.afterSales }}</text>
        <text class="ast-label">退款/售后</text>
      </view>
    </view>

    <!-- 功能菜单白卡 -->
    <view class="card menu-card">
      <text class="menu-t">购物助手</text>
      <view class="menu-row" @click="onAiChat" role="button" aria-label="AI 智能客服">
        <wd-icon name="chat1" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">AI 智能客服</text>
        <view class="menu-v">
          <text class="menu-value">推荐搭配 · 售后咨询</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
      <view class="menu-row" @click="onScanTrace" role="button" aria-label="产地溯源查询">
        <wd-icon name="qrcode" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">产地溯源查询</text>
        <view class="menu-v">
          <text class="menu-value">扫码溯源码</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
      <view class="menu-row menu-row--last" @click="goToSeckill" role="button" aria-label="限时秒杀">
        <wd-icon name="discount" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">限时秒杀</text>
        <text class="menu-dot"></text>
      </view>
    </view>

    <view class="card menu-card">
      <text class="menu-t">常用工具</text>
      <view class="menu-row" @click="goToCouponCenter" role="button" aria-label="领券中心">
        <wd-icon name="discount-filled" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">领券中心</text>
        <view class="menu-v">
          <text class="menu-value">好货更省钱</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
      <view class="menu-row" @click="goToMyCoupons" role="button" aria-label="我的券包">
        <wd-icon name="wallet" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">我的券包</text>
        <view class="menu-v">
          <text class="menu-value">{{ assetCounts.coupon }} 张可用</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
      <view class="menu-row" @click="goToAddress" role="button" aria-label="收货地址管理">
        <wd-icon name="location" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">收货地址管理</text>
        <view class="menu-v">
          <text class="menu-value">{{ assetCounts.address }} 个地址</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
      <view class="menu-row menu-row--last" @click="goToMyReviews" role="button" aria-label="我的评价">
        <wd-icon name="evaluation" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">我的评价</text>
        <view class="menu-v">
          <text class="menu-value">{{ assetCounts.review }} 条</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
    </view>

    <view class="card menu-card">
      <view v-if="servicePhone" class="menu-row" @click="onCallService" role="button" aria-label="帮助与客服">
        <wd-icon name="service" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">帮助与客服</text>
        <view class="menu-v">
          <text class="menu-value">{{ servicePhone }}</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
      <view class="menu-row menu-row--last" @click="goToAbout" role="button" aria-label="关于我们">
        <wd-icon name="info-circle-filled" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">关于我们</text>
        <view class="menu-v">
          <text class="menu-value">{{ platformName }} v1.0</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
    </view>

    <!-- 退出登录 -->
    <view v-if="isUserLoggedIn" class="logout-wrap">
      <view class="logout-btn" @click="onLogout" role="button">退出登录</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import { getUserInfo, updateUserInfo, logout, getAddressList } from '../../api/user'
import { getOrderList } from '../../api/order'
import { getMyCoupons } from '../../api/coupon'
import { getMyReviews } from '../../api/review'
import { getAfterSalesList } from '../../api/afterSales'
import { wxLogin, isLoggedIn, request } from '../../utils/request'
import { uploadMiniImage } from '../../utils/upload'

const userInfo = ref(isLoggedIn() ? uni.getStorageSync('userInfo') || null : null)
const orderCounts = ref({ 0: 0, 1: 0, 2: 0, 3: 0 })
const assetCounts = ref({ coupon: 0, review: 0, address: 0, afterSales: 0 })
const isUserLoggedIn = ref(isLoggedIn())
const servicePhone = ref('')
const platformName = ref('农产品商城')

const phoneText = computed(() => {
  const p = userInfo.value?.phone
  if (!p) return '点击完善个人资料'
  return p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p
})

const orderTabs = [
  { label: '待付款', status: 0, icon: 'money-circle', count: 0 },
  { label: '待发货', status: 1, icon: 'bags', count: 0 },
  { label: '待收货', status: 2, icon: 'cart', count: 0 },
  { label: '已完成', status: 3, icon: 'check-bold', count: 0 }
]

onShow(() => loadPageData())

/** 下拉刷新：订单角标与资产计数即时重取，不用退出重进 */
onPullDownRefresh(async () => {
  try {
    await loadPageData()
  } catch (e) {
    console.warn('[Mine] 下拉刷新失败', e)
  } finally {
    uni.stopPullDownRefresh()
  }
})

async function loadPageData() {
  isUserLoggedIn.value = isLoggedIn()
  if (isUserLoggedIn.value) {
    await loadUserInfo()
    await loadOrderCounts()
    loadAssetCounts()
  }
  await loadServicePhone()
}

// 订单列表页查看完某个状态后，立即把对应角标清零，返回时无需等 onShow 重新拉接口
uni.$on('orderBadgeCleared', onOrderBadgeCleared)
onUnload(() => {
  uni.$off('orderBadgeCleared', onOrderBadgeCleared)
})
function onOrderBadgeCleared(status) {
  if (status === null || status === undefined) return
  if (orderTabs[status]) orderTabs[status].count = 0
  orderCounts.value[status] = 0
}

/** 加载系统配置中的客服电话与平台名称（客服电话未配置则隐藏"帮助与客服"入口） */
async function loadServicePhone() {
  try {
    const res = await request('GET', '/system/config', null, { params: { keys: 'service_phone,platform_name' } })
    if (res.code === 0 && res.data?.service_phone) {
      servicePhone.value = res.data.service_phone
    }
    if (res.code === 0 && res.data?.platform_name) {
      platformName.value = res.data.platform_name
      uni.setStorageSync('platform_name', res.data.platform_name)
      uni.setNavigationBarTitle({ title: res.data.platform_name })
    }
  } catch (e) {
    console.warn('加载客服电话失败', e)
    // 接口失败时用本地缓存的平台名兜底标题
    const cached = uni.getStorageSync('platform_name')
    if (cached) {
      platformName.value = cached
      uni.setNavigationBarTitle({ title: cached })
    }
  }
}

function onAiChat() {
  uni.navigateTo({ url: '/pages/ai-chat/chat' })
}

function onCallService() {
  if (!servicePhone.value) return
  uni.makePhoneCall({ phoneNumber: servicePhone.value, fail: () => {} })
}

function goToSeckill() {
  uni.navigateTo({ url: '/pages/seckill/list' })
}

async function loadUserInfo() {
  const res = await getUserInfo()
  if (res.code === 0) {
    userInfo.value = res.data
    uni.setStorageSync('userInfo', res.data)
  }
}

async function loadOrderCounts() {
  for (const status of [0, 1, 2, 3]) {
    const res = await getOrderList({ pageNum: 1, pageSize: 1, status })
    if (res.code === 0) {
      const total = res.data?.total || 0
      const lastTotal = uni.getStorageSync('order_last_total_' + status) || 0
      const unread = Math.max(0, total - lastTotal)
      orderCounts.value[status] = unread
      orderTabs[status].count = unread
    }
  }
}

/** 资产四列：优惠券 / 我的评价 / 收货地址 / 退款售后，全部取自真实接口计数 */
async function loadAssetCounts() {
  try {
    const res = await getMyCoupons({ pageNum: 1, pageSize: 1, status: 0 })
    if (res.code === 0) assetCounts.value.coupon = res.data?.total || 0
  } catch (e) {
    console.warn('[Mine] 优惠券数量加载失败', e)
  }
  try {
    const res = await getMyReviews(1, 1)
    if (res.code === 0) assetCounts.value.review = res.data?.total || 0
  } catch (e) {
    console.warn('[Mine] 评价数量加载失败', e)
  }
  try {
    const res = await getAddressList()
    if (res.code === 0) {
      assetCounts.value.address = Array.isArray(res.data) ? res.data.length : (res.data?.total || 0)
    }
  } catch (e) {
    console.warn('[Mine] 地址数量加载失败', e)
  }
  try {
    const res = await getAfterSalesList({ pageNum: 1, pageSize: 1 })
    if (res.code === 0) assetCounts.value.afterSales = res.data?.total || 0
  } catch (e) {
    console.warn('[Mine] 售后数量加载失败', e)
  }
}

async function onLogin() {
  uni.showLoading({ title: '登录中...', mask: true })
  const success = await wxLogin()
  uni.hideLoading()
  if (success) {
    isUserLoggedIn.value = true
    userInfo.value = uni.getStorageSync('userInfo')
    uni.showToast({ title: '登录成功', icon: 'success' })
    await loadUserInfo()
    await loadOrderCounts()
    loadAssetCounts()
  } else {
    uni.showModal({
      title: '登录失败',
      content: '登录失败，请检查网络连接或稍后重试。',
      showCancel: false
    })
  }
}

function onOrderTabTap(status) {
  uni.navigateTo({ url: `/pages/order/list/list?status=${status}` })
}

function goToCouponCenter() {
  uni.navigateTo({ url: '/pages/coupon/center' })
}

function goToMyCoupons() {
  uni.navigateTo({ url: '/pages/coupon/my' })
}

function goToAddress() {
  uni.navigateTo({ url: '/pages/mine/address' })
}

function goToMyReviews() {
  uni.navigateTo({ url: '/pages/review/list/list' })
}

function goToAfterSales() {
  uni.navigateTo({ url: '/pages/after-sales/list/list' })
}

/** 查看全部订单：进入订单列表页（不带状态筛选） */
function goToAllOrders() {
  uni.navigateTo({ url: '/pages/order/list/list' })
}

/** 扫码溯源：识别二维码中的批次号，跳转溯源档案 */
function onScanTrace() {
  uni.scanCode({
    success: (res) => {
      const result = String(res.result || '').trim()
      let batchNo = ''
      // 二维码内容为 URL（.../api/trace?batchNo=B202609110001）
      const m = result.match(/[?&]batchNo=([^&]+)/)
      if (m && m[1]) {
        batchNo = decodeURIComponent(m[1])
      } else if (/^B\d{12}$/.test(result)) {
        // 二维码内容为纯批次号
        batchNo = result
      }
      if (batchNo) {
        uni.navigateTo({ url: `/pages/trace/detail?batchNo=${encodeURIComponent(batchNo)}` })
      } else {
        uni.showToast({ title: '未识别到溯源批次号', icon: 'none' })
      }
    },
    fail: () => {
      // 用户取消扫码，不做处理
    }
  })
}

function goToProfile() {
  uni.navigateTo({ url: '/pages/mine/profile' })
}

function goToSettings() {
  uni.navigateTo({ url: '/pages/mine/settings' })
}

function goToAbout() {
  uni.navigateTo({ url: '/pages/mine/about' })
}

async function onLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: async (res) => {
      if (res.confirm) {
        await logout()
        uni.removeStorageSync('token')
        uni.removeStorageSync('userInfo')
        isUserLoggedIn.value = false
        userInfo.value = null
        uni.showToast({ title: '已退出登录', icon: 'success' })
      }
    }
  })
}

async function onChooseAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const filePath = res.tempFilePaths[0]
      uni.showLoading({ title: '上传中...', mask: true })
      try {
        const url = await uploadMiniImage(filePath, 'user/avatar')
        await updateUserInfo({ avatar: url })
        userInfo.value = { ...userInfo.value, avatar: url }
        uni.setStorageSync('userInfo', userInfo.value)
        uni.showToast({ title: '头像已更新', icon: 'success' })
      } catch {
      } finally {
        uni.hideLoading()
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
  padding-bottom: 40rpx;
}

/* ===== 头部 ===== */
.mhd {
  display: flex;
  align-items: center;
  gap: $uni-spacing-col-base;
  padding: 28rpx;
  padding-bottom: 32rpx;
  background: $uni-bg-color;
}

.mhd-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  transition: opacity 150ms ease-out;
}

.mhd-info:active {
  opacity: 0.75;
}

.mhd-name-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.mhd-name {
  font-size: 32rpx;
  font-weight: 600;
  color: $uni-text-color;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

:deep(.mhd-edit-ic) {
  color: $uni-text-color-placeholder;
  flex-shrink: 0;
}

.mhd-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
}

.mhd-qr {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.mhd-qr--gap {
  margin-left: 28rpx;
}

.mhd-qr-t {
  font-size: 18rpx;
  color: $uni-text-color-placeholder;
}

:deep(.mhd-qr-ic) {
  color: $uni-text-color-grey;
}

.mhd-av {
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: $uni-bg-color-img;
  border: 2rpx solid $uni-border-color;
  flex-shrink: 0;
  transition: opacity 150ms ease-out;
}

.mhd-av:active {
  opacity: 0.75;
}

.mhd-av--ph {
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.mhd-av-ic) {
  color: $uni-color-img-icon;
}

/* ===== 卡片通用 ===== */
.card {
  background: $uni-bg-color;
  margin: $uni-spacing-col-lg;
  border-radius: $uni-border-radius-base;
  box-shadow: $uni-shadow-sm;
}

/* ===== 订单区 ===== */
.ord-card {
  margin: 16rpx $uni-spacing-col-lg;
  padding: $uni-spacing-col-lg 0 28rpx;
}

.ord-hd {
  display: flex;
  align-items: center;
  padding: 0 $uni-spacing-col-lg $uni-spacing-col-lg;
}

.ord-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.ord-all {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2rpx;
}

.ord-all-txt {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
}

:deep(.ord-all-ic) {
  color: $uni-text-color-placeholder;
}

.ord-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
}

.ord-tab {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 16rpx 0;
  min-height: 88rpx;
  transition: opacity 150ms ease-out;
}

.ord-tab:active {
  opacity: 0.7;
}

:deep(.ord-ic) {
  color: $uni-text-color;
}

.ord-label {
  font-size: 22rpx;
  color: $uni-text-color-grey;
}

.ord-cb {
  position: absolute;
  top: 8rpx;
  right: calc(50% - 44rpx);
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 6rpx;
  border-radius: 14rpx;
  background: $uni-color-primary;
  color: $uni-text-color-inverse;
  font-size: 18rpx;
  line-height: 28rpx;
  font-weight: 600;
  text-align: center;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
}

/* ===== 资产四列 ===== */
.ast-card {
  display: flex;
  margin: 0 $uni-spacing-col-lg 16rpx;
  padding: $uni-spacing-col-lg 0;
}

.ast-col {
  flex: 1;
  text-align: center;
  border-right: 2rpx solid $uni-border-color-divider;
}

.ast-col--last {
  border-right: none;
}

.ast-num {
  display: block;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  font-size: 34rpx;
  font-weight: 700;
  color: $uni-text-color;
  line-height: 1.2;
}

.ast-label {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
}

/* ===== 功能菜单 ===== */
.menu-card {
  margin: 0 $uni-spacing-col-lg 16rpx;
  padding: 0;
  overflow: hidden;
}

.menu-t {
  display: block;
  padding: $uni-spacing-col-lg $uni-spacing-col-lg 4rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.menu-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: $uni-spacing-col-lg;
  min-height: 88rpx;
  box-sizing: border-box;
  border-bottom: 2rpx solid $uni-border-color-divider;
  transition: opacity 150ms ease-out;
}

.menu-row:active {
  opacity: 0.75;
}

.menu-row--last {
  border-bottom: none;
}

:deep(.menu-ic) {
  color: $uni-text-color-grey;
  flex-shrink: 0;
}

.menu-label {
  font-size: 26rpx;
  color: $uni-text-color;
}

.menu-v {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4rpx;
  min-width: 0;
}

.menu-value {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

:deep(.menu-chev) {
  color: $uni-text-color-disable;
}

.menu-dot {
  margin-left: auto;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: $uni-color-primary;
}

/* ===== 退出登录 ===== */
.logout-wrap {
  padding: 28rpx $uni-spacing-col-lg 36rpx;
}

.logout-btn {
  height: 88rpx;
  line-height: 84rpx;
  text-align: center;
  background: $uni-bg-color;
  border: 2rpx solid $uni-border-color;
  border-radius: 44rpx;
  color: $uni-text-color-grey;
  font-size: 28rpx;
  font-weight: 400;
  box-sizing: border-box;
}
</style>
