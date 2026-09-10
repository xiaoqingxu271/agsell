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
import { onShow } from '@dcloudio/uni-app'
import { getUserInfo, updateUserInfo, logout } from '../../api/user'
import { getOrderList } from '../../api/order'
import { wxLogin, isLoggedIn } from '../../utils/request'
import { uploadMiniImage } from '../../utils/upload'

const userInfo = ref(null)
const orderCounts = ref({ 0: 0, 1: 0, 2: 0, 3: 0 })
const isUserLoggedIn = ref(isLoggedIn())

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
  background: #F4F6F5;
  padding-bottom: 40rpx;
}

.user-header {
  display: flex;
  align-items: center;
  padding: 56rpx 40rpx 64rpx;
  background: linear-gradient(160deg, #15803D 0%, #166534 100%);
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
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #15803D;
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
  transition: background 150ms ease-out;
}

.order-tab:active {
  background: #F4F9F5;
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
  border-bottom: 1rpx solid #EEF1EF;
  min-height: 88rpx;
  box-sizing: border-box;
  transition: background 150ms ease-out;
}

.menu-item:active {
  background: #F4F9F5;
}

.menu-item:last-child { border-bottom: none; }

.menu-icon {
  width: 40rpx;
  height: 40rpx;
  margin-right: 20rpx;
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
  flex-shrink: 0;
}

.logout-btn {
  margin: 40rpx 24rpx;
  text-align: center;
  border: 1rpx solid #DC2626;
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
