<template>
  <view class="ab-page">
    <NavBar title="关于我们" @back="uni.navigateBack()" />

    <view class="brand">
      <view class="brand-mark">
        <text class="brand-mark-t">{{ brandChar }}</text>
      </view>
      <text class="brand-name">{{ platformName }}</text>
      <text class="brand-ver">版本 v{{ appVersion }}</text>
    </view>

    <view class="card">
      <text class="intro-t">平台简介</text>
      <text class="intro">
        聚焦产地直供的农产品电商平台，提供商品选购、订单物流、产地溯源、限时秒杀与优惠券等服务，
        并由 AI 导购助手提供推荐搭配与售后咨询能力。
      </text>
    </view>

    <view v-if="servicePhone || isUserLoggedIn" class="card menu-card">
      <view v-if="servicePhone" class="menu-row" :class="{ 'menu-row--last': !isUserLoggedIn }" @click="onCallService" role="button" aria-label="客服电话">
        <wd-icon name="service" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">客服电话</text>
        <view class="menu-v">
          <text class="menu-value">{{ servicePhone }}</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="menu-chev" />
        </view>
      </view>
      <view v-if="isUserLoggedIn" class="menu-row menu-row--last">
        <wd-icon name="user" size="36rpx" custom-class="menu-ic" />
        <text class="menu-label">当前账号</text>
        <view class="menu-v">
          <text class="menu-value">{{ nickname }}</text>
        </view>
      </view>
    </view>

    <view class="foot">
      <text class="foot-line">{{ platformName }} · 毕业设计演示环境</text>
      <text class="foot-line">商品与订单数据均为测试数据</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { isLoggedIn, request } from '../../utils/request'

const platformName = ref('农产品商城')
const servicePhone = ref('')
const nickname = ref('')
const isUserLoggedIn = ref(isLoggedIn())
const appVersion = '1.0'

const brandChar = computed(() => platformName.value.charAt(0) || '农')

onShow(() => {
  isUserLoggedIn.value = isLoggedIn()
  const cached = uni.getStorageSync('userInfo')
  nickname.value = cached?.nickname || ''
  const cachedName = uni.getStorageSync('platform_name')
  if (cachedName) platformName.value = cachedName
  loadConfig()
})

async function loadConfig() {
  try {
    const res = await request('GET', '/system/config', null, { params: { keys: 'service_phone,platform_name' } })
    if (res.code === 0) {
      if (res.data?.service_phone) servicePhone.value = res.data.service_phone
      if (res.data?.platform_name) {
        platformName.value = res.data.platform_name
        uni.setStorageSync('platform_name', res.data.platform_name)
      }
    }
  } catch (e) {
    console.warn('[About] 系统配置加载失败', e)
  }
}

function onCallService() {
  if (!servicePhone.value) return
  uni.makePhoneCall({ phoneNumber: servicePhone.value, fail: () => {} })
}
</script>

<style lang="scss" scoped>
.ab-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
  padding-bottom: 40rpx;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56rpx 0 40rpx;
}

.brand-mark {
  width: 128rpx;
  height: 128rpx;
  border-radius: $uni-border-radius-lg;
  background: $uni-color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $uni-shadow-seckill;
}

.brand-mark-t {
  font-size: 60rpx;
  font-weight: 700;
  color: $uni-text-color-inverse;
  line-height: 1;
}

.brand-name {
  margin-top: 24rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.brand-ver {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
}

.card {
  background: $uni-bg-color;
  margin: 0 $uni-spacing-col-lg 16rpx;
  border-radius: $uni-border-radius-base;
  box-shadow: $uni-shadow-sm;
  padding: $uni-spacing-col-lg;
}

.intro-t {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: $uni-text-color;
  margin-bottom: 12rpx;
}

.intro {
  display: block;
  font-size: 26rpx;
  line-height: 1.7;
  color: $uni-text-color-grey;
}

.menu-card {
  padding: 0;
  overflow: hidden;
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

.foot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 48rpx $uni-spacing-col-lg 0;
}

.foot-line {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
}
</style>
