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
            <svg class="tab-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path :d="item.iconPath" />
            </svg>
          </view>
          <text v-if="item.count > 0" class="tab-count">{{ item.count }}</text>
          <text class="tab-label">{{ item.label }}</text>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section card">
      <view class="menu-item" @click="goToAddress" role="button">
        <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
          <circle cx="12" cy="10" r="3"></circle>
        </svg>
        <text class="menu-text">收货地址</text>
        <svg class="menu-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="9 18 15 12 9 6"></polyline>
        </svg>
      </view>
      <view class="menu-item" @click="goToMyReviews" role="button">
        <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
        </svg>
        <text class="menu-text">我的评价</text>
        <svg class="menu-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="9 18 15 12 9 6"></polyline>
        </svg>
      </view>
      <view class="menu-item" @click="goToAfterSales" role="button">
        <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path>
          <path d="M3 3v5h5"></path>
        </svg>
        <text class="menu-text">我的售后</text>
        <svg class="menu-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="9 18 15 12 9 6"></polyline>
        </svg>
      </view>
      <view class="menu-item" @click="goToAbout" role="button">
        <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="12" y1="16" x2="12" y2="12"></line>
          <line x1="12" y1="8" x2="12.01" y2="8"></line>
        </svg>
        <text class="menu-text">关于我们</text>
        <svg class="menu-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="9 18 15 12 9 6"></polyline>
        </svg>
      </view>
    </view>

    <!-- 退出登录 -->
    <view v-if="isUserLoggedIn" class="logout-btn" @click="onLogout" role="button">退出登录</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getUserInfo, updateUserInfo, logout } from '../../api/user'
import { getOrderList } from '../../api/order'
import { wxLogin, isLoggedIn } from '../../utils/request'
import { uploadMiniImage } from '../../utils/upload'

const userInfo = ref(null)
const orderCounts = ref({ 0: 0, 1: 0, 2: 0, 3: 0 })
const isUserLoggedIn = ref(isLoggedIn())

const orderTabs = [
  { label: '待付款', status: 0, iconPath: 'M2 5h20a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM2 10h20', count: 0 },
  { label: '待发货', status: 1, iconPath: 'M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16zM3.27 6.96 12 12.01l8.73-5.05M12 22.08V12', count: 0 },
  { label: '待收货', status: 2, iconPath: 'M1 3h15v13H1zM16 8h4l3 3v5h-7V8zM5.5 18.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM18.5 18.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z', count: 0 },
  { label: '已完成', status: 3, iconPath: 'M22 11.08V12a10 10 0 1 1-5.93-9.14M22 4 12 14.01l-3-3', count: 0 }
]

onShow(async () => {
  isUserLoggedIn.value = isLoggedIn()
  if (isUserLoggedIn.value) {
    await loadUserInfo()
    await loadOrderCounts()
  }
})

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
      orderCounts.value[status] = res.data?.total || 0
      orderTabs[status].count = res.data?.total || 0
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

function goToAbout() {
  uni.showToast({ title: 'agsell 农产品销售系统 v1.0', icon: 'none' })
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
  background: #F0FDF4;
  padding-bottom: 40rpx;
}

.user-header {
  display: flex;
  align-items: center;
  padding: 60rpx 40rpx;
  background: linear-gradient(135deg, #15803D 0%, #22C55E 100%);
}

.login-btn {
  margin-left: 24rpx;
  width: 240rpx;
  height: 72rpx;
  line-height: 72rpx;
  background: #FFFFFF;
  color: #15803D;
  font-size: 28rpx;
  font-weight: 600;
  border-radius: 36rpx;
  border: 2rpx solid #FFFFFF;
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
  background: rgba(255, 255, 255, 0.3);
  border: 4rpx solid rgba(255, 255, 255, 0.5);
  flex-shrink: 0;
}

.user-header.login-prompt .user-avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
}

.avatar-tip {
  margin-left: 24rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(255, 255, 255, 0.2);
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
  color: rgba(255, 255, 255, 0.8);
  margin-top: 8rpx;
  display: block;
}

.card {
  background: #FFFFFF;
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1px solid #BBF7D0;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #14532D;
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
  color: #6B7280;
}

.tab-count {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  font-size: 20rpx;
  font-weight: 600;
  color: #FFFFFF;
  background: #DC2626;
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  border-radius: 16rpx;
  padding: 0 8rpx;
  text-align: center;
}

.tab-label {
  font-size: 24rpx;
  color: #6B7280;
}

.menu-section {
  margin: 24rpx;
  padding: 0 24rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 32rpx 0;
  border-bottom: 1px solid #E5E7EB;
  min-height: 88rpx;
  box-sizing: border-box;
}

.menu-item:last-child { border-bottom: none; }

.menu-icon {
  width: 40rpx;
  height: 40rpx;
  margin-right: 20rpx;
  color: #15803D;
  flex-shrink: 0;
}

.menu-text {
  flex: 1;
  font-size: 30rpx;
  color: #1F2937;
}

.menu-arrow {
  width: 32rpx;
  height: 32rpx;
  color: #9CA3AF;
  flex-shrink: 0;
}

.logout-btn {
  margin: 40rpx 24rpx;
  text-align: center;
  padding: 24rpx;
  border: 1px solid #DC2626;
  border-radius: 44rpx;
  color: #DC2626;
  font-size: 30rpx;
  font-weight: 500;
  min-height: 88rpx;
  line-height: 88rpx;
  box-sizing: border-box;
  padding: 0;
}
</style>
