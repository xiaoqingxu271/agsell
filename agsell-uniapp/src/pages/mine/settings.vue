<template>
  <view class="set-page">
    <NavBar title="设置" @back="uni.navigateBack()" />

    <view class="card menu-card">
      <text class="menu-t">账号与安全</text>
      <view class="menu-row menu-row--last" @click="goToProfile" role="button" aria-label="编辑资料">
        <wd-icon name="user" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">编辑资料</text>
        <view class="menu-v">
          <text class="menu-value">{{ nickname }}</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
    </view>

    <view class="card menu-card">
      <text class="menu-t">通用</text>
      <view class="menu-row" @click="onClearCache" role="button" aria-label="清除缓存">
        <wd-icon name="delete" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">清除缓存</text>
        <view class="menu-v">
          <text class="menu-value">{{ cacheText }}</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
      <view class="menu-row menu-row--last" @click="goToAbout" role="button" aria-label="关于我们">
        <wd-icon name="info-circle-filled" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">关于我们</text>
        <view class="menu-v">
          <text class="menu-value">v{{ appVersion }}</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
    </view>

    <view v-if="servicePhone" class="card menu-card">
      <text class="menu-t">帮助</text>
      <view class="menu-row menu-row--last" @click="onCallService" role="button" aria-label="客服电话">
        <wd-icon name="service" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">客服电话</text>
        <view class="menu-v">
          <text class="menu-value">{{ servicePhone }}</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
    </view>

    <view v-if="isUserLoggedIn" class="logout-wrap">
      <view class="logout-btn" @click="onLogout" role="button">退出登录</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getUserInfo, logout } from '../../api/user'
import { isLoggedIn, request } from '../../utils/request'

// 清缓存必须保留的键：登录态与平台名不是缓存
const KEEP_KEYS = ['token', 'userInfo', 'platform_name']

const nickname = ref('未设置')
const servicePhone = ref('')
const cacheSizeKb = ref(0)
const isUserLoggedIn = ref(isLoggedIn())
const appVersion = '1.0'

const cacheText = computed(() => `${cacheSizeKb.value} KB`)

onShow(() => {
  isUserLoggedIn.value = isLoggedIn()
  readCacheSize()
  if (isUserLoggedIn.value) loadUserInfo()
  loadConfig()
})

async function loadUserInfo() {
  try {
    const res = await getUserInfo()
    if (res.code === 0 && res.data?.nickname) {
      nickname.value = res.data.nickname
      uni.setStorageSync('userInfo', res.data)
    }
  } catch (e) {
    const cached = uni.getStorageSync('userInfo')
    if (cached?.nickname) nickname.value = cached.nickname
  }
}

async function loadConfig() {
  try {
    const res = await request('GET', '/system/config', null, { params: { keys: 'service_phone' } })
    if (res.code === 0 && res.data?.service_phone) {
      servicePhone.value = res.data.service_phone
    }
  } catch (e) {
    console.warn('[Settings] 客服电话加载失败', e)
  }
}

function readCacheSize() {
  try {
    cacheSizeKb.value = uni.getStorageInfoSync().currentSize || 0
  } catch (e) {
    cacheSizeKb.value = 0
  }
}

function onClearCache() {
  let removable = []
  try {
    removable = (uni.getStorageInfoSync().keys || []).filter((k) => !KEEP_KEYS.includes(k))
  } catch (e) {
    uni.showToast({ title: '缓存已清空', icon: 'success' })
    return
  }
  if (removable.length === 0) {
    uni.showToast({ title: '暂无可清除的缓存', icon: 'none' })
    return
  }
  uni.showModal({
    title: '清除缓存',
    content: `将清除 ${removable.length} 项本地缓存（订单角标记录等），不影响登录状态。`,
    confirmText: '清除',
    success: (res) => {
      if (!res.confirm) return
      removable.forEach((k) => uni.removeStorageSync(k))
      readCacheSize()
      uni.showToast({ title: '已清除', icon: 'success' })
    }
  })
}

function onCallService() {
  if (!servicePhone.value) return
  uni.makePhoneCall({ phoneNumber: servicePhone.value, fail: () => {} })
}

function goToProfile() {
  uni.navigateTo({ url: '/pages/mine/profile' })
}

function goToAbout() {
  uni.navigateTo({ url: '/pages/mine/about' })
}

function onLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: async (res) => {
      if (res.confirm) {
        await logout()
        uni.removeStorageSync('token')
        uni.removeStorageSync('userInfo')
        uni.switchTab({ url: '/pages/mine/mine' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.set-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
  padding-bottom: 40rpx;
}

.card {
  background: $uni-bg-color;
  margin: $uni-spacing-col-lg;
  border-radius: $uni-border-radius-base;
  box-shadow: $uni-shadow-sm;
}

.menu-card {
  margin: 0 $uni-spacing-col-lg 16rpx;
  padding: 0;
  overflow: hidden;

  &:first-of-type {
    margin-top: $uni-spacing-col-lg;
  }
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
  box-sizing: border-box;
}
</style>
