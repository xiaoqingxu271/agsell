<template>
  <view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="nav-content" :style="{ height: navBarHeight + 'px' }">
      <view v-if="showBack" class="nav-back" aria-label="返回" @click="$emit('back')">
        <svg class="back-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
      </view>
      <text class="nav-title">{{ title }}</text>
      <view class="nav-right"><slot name="right" /></view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  showBack: { type: Boolean, default: true }
})
defineEmits(['back'])

const statusBarHeight = ref(0)
const navBarHeight = ref(44)

onMounted(() => {
  const systemInfo = uni.getSystemInfoSync()
  statusBarHeight.value = systemInfo.statusBarHeight
  navBarHeight.value = 44
})
</script>

<style scoped>
.nav-bar {
  width: 100%;
  background: #15803D;
}
.nav-content {
  display: flex;
  align-items: center;
  padding: 0 24rpx;
}
.nav-back {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: -12rpx;
}
.back-icon {
  width: 40rpx;
  height: 40rpx;
  color: #FFFFFF;
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  color: #FFFFFF;
  font-weight: 600;
  margin-left: -88rpx;
}
.nav-right {
  width: 88rpx;
  display: flex;
  justify-content: flex-end;
}
</style>
