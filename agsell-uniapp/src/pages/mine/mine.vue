<template>
  <view class="mine-page">
    <!-- 用户信息区 -->
    <view v-if="isUserLoggedIn" class="user-header" @click="onChooseAvatar">
      <image
        class="user-avatar"
        :src="userInfo?.avatar || '/static/default-avatar.png'"
        mode="aspectFill"
        alt="用户头像"
      />
      <view class="user-info">
        <text class="user-name">{{ userInfo?.nickname || '微信用户' }}</text>
        <text class="user-phone">{{ userInfo?.phone || '暂无绑定手机号' }}</text>
      </view>
      <text class="avatar-tip">点击更换头像</text>
    </view>
    <view v-else class="user-header login-prompt">
      <image class="user-avatar" src="/static/default-avatar.png" mode="aspectFill" alt="默认头像" />
      <view class="user-info">
        <text class="user-name">登录后享受更多权益</text>
        <text class="user-phone">订单管理 · 收货地址 · 商品评价</text>
      </view>
      <button class="login-btn" @click.stop="onLogin">微信一键登录</button>
    </view>

    <!-- 订单快捷入口 -->
    <view class="order-section card">
      <view class="section-title">我的订单</view>
      <view class="order-tabs">
        <view
          v-for="(item, index) in orderTabs"
          :key="index"
          class="order-tab"
          @click="onOrderTabTap(item.status)"
          role="button"
          :aria-label="item.label"
        >
          <view class="tab-icon-wrap">
            <image class="tab-icon" :src="item.icon" mode="aspectFit" alt="待付款" />
          </view>
          <text v-if="item.count > 0" class="tab-count">{{ item.count }}</text>
          <text class="tab-label">{{ item.label }}</text>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section card">
      <view class="menu-item" @click="goToAddress" role="button">
        <image class="menu-icon" src="/static/icon-menu-address.png" mode="aspectFit" alt="收货地址" />
        <text class="menu-text">收货地址</text>
        <image class="menu-arrow" src="/static/icon-menu-arrow.png" mode="aspectFit" alt="进入" />
      </view>
      <view class="menu-item" @click="goToMyReviews" role="button">
        <image class="menu-icon" src="/static/icon-menu-review.png" mode="aspectFit" alt="我的评价" />
        <text class="menu-text">我的评价</text>
        <image class="menu-arrow" src="/static/icon-menu-arrow.png" mode="aspectFit" alt="进入" />
      </view>
      <view class="menu-item" @click="goToAfterSales" role="button">
        <image class="menu-icon" src="/static/icon-menu-aftersale.png" mode="aspectFit" alt="我的售后" />
        <text class="menu-text">我的售后</text>
        <image class="menu-arrow" src="/static/icon-menu-arrow.png" mode="aspectFit" alt="进入" />
      </view>
      <view class="menu-item" @click="onScanTrace" role="button">
        <view class="menu-icon menu-icon-qr" aria-hidden="true"></view>
        <text class="menu-text">扫码溯源</text>
        <image class="menu-arrow" src="/static/icon-menu-arrow.png" mode="aspectFit" alt="进入" />
      </view>
      <view v-if="servicePhone" class="menu-item" @click="onContactService" role="button">
        <view class="menu-icon menu-icon-service" aria-hidden="true"></view>
        <text class="menu-text">AI 智能客服</text>
        <text class="menu-phone">AI 客服 · {{ servicePhone }}</text>
        <image class="menu-arrow" src="/static/icon-menu-arrow.png" mode="aspectFit" alt="进入" />
      </view>
      <view class="menu-item" @click="goToAbout" role="button">
        <image class="menu-icon" src="/static/icon-menu-about.png" mode="aspectFit" alt="关于我们" />
        <text class="menu-text">关于我们</text>
        <image class="menu-arrow" src="/static/icon-menu-arrow.png" mode="aspectFit" alt="进入" />
      </view>
    </view>

    <!-- 退出登录 -->
    <view v-if="isUserLoggedIn" class="logout-btn" @click="onLogout" role="button">退出登录</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { getUserInfo, updateUserInfo, logout } from '../../api/user'
import { getOrderList } from '../../api/order'
import { wxLogin, isLoggedIn, request } from '../../utils/request'
import { uploadMiniImage } from '../../utils/upload'

const userInfo = ref(null)
const orderCounts = ref({ 0: 0, 1: 0, 2: 0, 3: 0 })
const isUserLoggedIn = ref(isLoggedIn())
const servicePhone = ref('')

const orderTabs = [
  { label: '待付款', status: 0, icon: '/static/icon-order-pay.png', count: 0 },
  { label: '待发货', status: 1, icon: '/static/icon-order-package.png', count: 0 },
  { label: '待收货', status: 2, icon: '/static/icon-order-truck.png', count: 0 },
  { label: '已完成', status: 3, icon: '/static/icon-order-done.png', count: 0 }
]

onShow(async () => {
  isUserLoggedIn.value = isLoggedIn()
  if (isUserLoggedIn.value) {
    await loadUserInfo()
    await loadOrderCounts()
  }
  loadServicePhone()
})

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

/** 加载系统配置中的客服电话与平台名称（客服电话未配置则隐藏"联系客服"入口） */
async function loadServicePhone() {
  try {
    const res = await request('GET', '/system/config', null, { params: { keys: 'service_phone,platform_name' } })
    if (res.code === 0 && res.data?.service_phone) {
      servicePhone.value = res.data.service_phone
    }
    if (res.code === 0 && res.data?.platform_name) {
      uni.setStorageSync('platform_name', res.data.platform_name)
      uni.setNavigationBarTitle({ title: res.data.platform_name })
    }
  } catch (e) {
    console.warn('加载客服电话失败', e)
    // 接口失败时用本地缓存的平台名兜底标题
    const cached = uni.getStorageSync('platform_name')
    if (cached) uni.setNavigationBarTitle({ title: cached })
  }
}

function onContactService() {
  // 进入 AI 智能客服聊天页（客服电话已在聊天页欢迎语/兜底话术内提示）
  uni.navigateTo({ url: '/pages/ai-chat/chat' })
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

function goToAddress() {
  uni.navigateTo({ url: '/pages/mine/address' })
}

function goToMyReviews() {
  uni.navigateTo({ url: '/pages/review/list/list' })
}

function goToAfterSales() {
  uni.navigateTo({ url: '/pages/after-sales/list/list' })
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

function goToAbout() {
  const platformName = uni.getStorageSync('platform_name') || '农产品商城'
  uni.showToast({ title: `${platformName} v1.0`, icon: 'none' })
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

<style scoped>
.mine-page {
  min-height: 100vh;
  background: #F5F5F5;
  padding-bottom: 40rpx;
}

.user-header {
  display: flex;
  align-items: center;
  padding: 56rpx 40rpx 64rpx;
  background: linear-gradient(135deg, #00B578 0%, #00C886 100%);
}

.login-btn {
  margin-left: 24rpx;
  width: 240rpx;
  height: 72rpx;
  line-height: 72rpx;
  background: #FFFFFF;
  color: #00B578;
  font-size: 28rpx;
  font-weight: 600;
  border-radius: 36rpx;
  border: none;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.login-btn::after {
  border: none;
}

.user-avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: #FFFFFF;
  border: 4rpx solid rgba(255, 255, 255, 0.5);
  flex-shrink: 0;
}

.user-header.login-prompt .user-avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: #FFFFFF;
  border: 4rpx solid rgba(255, 255, 255, 0.5);
}

.avatar-tip {
  margin-left: 24rpx;
  font-size: 22rpx;
  color: #FFFFFF;
  background: rgba(255, 255, 255, 0.25);
  padding: 8rpx 16rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}

.user-info {
  margin-left: 24rpx;
  flex: 1;
  overflow: hidden;
}

.user-name {
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
  display: block;
}

.user-phone {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 8rpx;
  display: block;
}

.card {
  background: #FFFFFF;
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 20rpx;
}

.order-tabs {
  display: flex;
  justify-content: space-around;
}

.order-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 16rpx;
  min-width: 88rpx;
  min-height: 88rpx;
  position: relative;
  border-radius: 16rpx;
  transition: opacity 150ms ease-out;
}

.order-tab:active {
  opacity: 0.7;
}

.tab-icon-wrap {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8rpx;
}

.tab-icon {
  width: 48rpx;
  height: 48rpx;
}

.tab-count {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  font-size: 20rpx;
  font-weight: 600;
  color: #FFFFFF;
  background: #E63946;
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  border-radius: 16rpx;
  padding: 0 8rpx;
  text-align: center;
}

.tab-label {
  font-size: 24rpx;
  color: #4B5563;
}

.menu-section {
  margin: 24rpx;
  padding: 0 24rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 32rpx 0;
  border-bottom: 1rpx solid #F0F0F0;
  min-height: 88rpx;
  box-sizing: border-box;
  transition: opacity 150ms ease-out;
}

.menu-item:active {
  opacity: 0.7;
}

.menu-item:last-child { border-bottom: none; }

.menu-icon {
  width: 40rpx;
  height: 40rpx;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.menu-icon-qr {
  border-radius: 8rpx;
  background: #00B578;
  position: relative;
}

.menu-icon-qr::before {
  content: '';
  position: absolute;
  left: 7rpx;
  top: 7rpx;
  width: 11rpx;
  height: 11rpx;
  border: 3rpx solid #FFFFFF;
  border-right: none;
  border-bottom: none;
  border-top-left-radius: 4rpx;
}

.menu-icon-qr::after {
  content: '';
  position: absolute;
  right: 7rpx;
  bottom: 7rpx;
  width: 11rpx;
  height: 11rpx;
  border: 3rpx solid #FFFFFF;
  border-left: none;
  border-top: none;
  border-bottom-right-radius: 4rpx;
}

.menu-icon-service {
  border-radius: 8rpx;
  background: #00B578;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu-icon-service::before {
  content: '☎';
  color: #FFFFFF;
  font-size: 26rpx;
  line-height: 1;
}

.menu-text {
  flex: 1;
  font-size: 30rpx;
  color: #111827;
}

.menu-arrow {
  width: 32rpx;
  height: 32rpx;
  flex-shrink: 0;
}

.menu-phone {
  font-size: 24rpx;
  color: #00B578;
  margin-right: 8rpx;
  flex-shrink: 0;
}

.logout-btn {
  margin: 40rpx 24rpx;
  text-align: center;
  border: none;
  background: #FFFFFF;
  border-radius: 24rpx;
  color: #DC2626;
  font-size: 30rpx;
  font-weight: 500;
  min-height: 88rpx;
  line-height: 88rpx;
  box-sizing: border-box;
  padding: 0;
}
</style>
