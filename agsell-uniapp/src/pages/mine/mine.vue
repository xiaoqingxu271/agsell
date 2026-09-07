<template>
  <view class="mine-page">
    <!-- 用户信息区 -->
    <view v-if="isUserLoggedIn" class="user-header" @click="onChooseAvatar">
      <image
        class="user-avatar"
        :src="userInfo?.avatar || '/static/default-avatar.png'"
        mode="aspectFill"
      />
      <view class="user-info">
        <text class="user-name">{{ userInfo?.nickname || '微信用户' }}</text>
        <text class="user-phone">{{ userInfo?.phone || '暂无绑定手机号' }}</text>
      </view>
      <text class="avatar-tip">点击更换头像</text>
    </view>
    <view v-else class="user-header login-prompt">
      <image class="user-avatar" src="/static/default-avatar.png" mode="aspectFill" />
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
        >
          <text class="tab-icon">{{ item.icon }}</text>
          <text class="tab-count">{{ item.count }}</text>
          <text class="tab-label">{{ item.label }}</text>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section card">
      <view class="menu-item" @click="goToAddress">
        <text class="menu-icon">📍</text>
        <text class="menu-text">收货地址</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goToMyReviews">
        <text class="menu-icon">⭐</text>
        <text class="menu-text">我的评价</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goToAbout">
        <text class="menu-icon">ℹ️</text>
        <text class="menu-text">关于我们</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 退出登录 -->
    <view v-if="isUserLoggedIn" class="logout-btn" @click="onLogout">退出登录</view>
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
// 用 ref 替代 computed，支持手动赋值
const isUserLoggedIn = ref(isLoggedIn())

const orderTabs = [
  { label: '待付款', status: 0, icon: '💳' },
  { label: '待发货', status: 1, icon: '📦' },
  { label: '待收货', status: 2, icon: '🚚' },
  { label: '已完成', status: 3, icon: '✅' }
]

onShow(async () => {
  // 每次从 storage 同步登录状态
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
    console.log('[mine] loadUserInfo:', res.data?.nickname, res.data?.avatar)
  }
}

async function loadOrderCounts() {
  for (const status of [0, 1, 2, 3]) {
    const res = await getOrderList({ pageNum: 1, pageSize: 1, status })
    if (res.code === 0) {
      orderCounts.value[status] = res.data?.total || 0
    }
  }
}

async function onLogin() {
  uni.showLoading({ title: '登录中...', mask: true })
  const success = await wxLogin()
  uni.hideLoading()
  if (success) {
    isUserLoggedIn.value = true
    // 从 storage 读取最新数据
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
        // error already shown inside uploadMiniImage
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
  background: #f5f5f5;
  padding-bottom: 40rpx;
}

.user-header {
  display: flex;
  align-items: center;
  padding: 60rpx 40rpx;
  background: linear-gradient(135deg, #4CAF50 0%, #81C784 100%);
}

.login-btn {
  margin-left: 24rpx;
  width: 240rpx;
  height: 72rpx;
  line-height: 72rpx;
  background: #fff;
  color: #4CAF50;
  font-size: 28rpx;
  font-weight: bold;
  border-radius: 36rpx;
  border: 2rpx solid #fff;
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
}

.user-name {
  font-size: 36rpx;
  font-weight: bold;
  color: #fff;
  display: block;
}

.user-phone {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 8rpx;
  display: block;
}

.card {
  background: #fff;
  margin: 20rpx;
  padding: 24rpx;
  border-radius: 16rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #333;
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
  padding: 16rpx 24rpx;
}

.tab-icon {
  font-size: 48rpx;
  margin-bottom: 8rpx;
}

.tab-count {
  font-size: 28rpx;
  font-weight: bold;
  color: #333;
  margin-bottom: 4rpx;
}

.tab-label {
  font-size: 24rpx;
  color: #999;
}

.menu-section {
  margin: 20rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 32rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.menu-item:last-child { border-bottom: none; }

.menu-icon {
  font-size: 40rpx;
  margin-right: 20rpx;
}

.menu-text {
  flex: 1;
  font-size: 30rpx;
  color: #333;
}

.menu-arrow {
  font-size: 32rpx;
  color: #ccc;
}

.logout-btn {
  margin: 40rpx 20rpx;
  text-align: center;
  padding: 24rpx;
  border: 1rpx solid #F44336;
  border-radius: 44rpx;
  color: #F44336;
  font-size: 30rpx;
}
</style>
