<template>
  <view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="nav-content" :style="{ height: navBarHeight + 'px' }">
      <view v-if="showBack" class="nav-back" aria-label="返回" @click="$emit('back')">
        <view class="nav-back-arrow"></view>
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
  background: #F0FDF4;
}
.nav-content {
  position: relative;
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
  flex-shrink: 0;
}
.nav-back-arrow {
  width: 22rpx;
  height: 22rpx;
  border-left: 5rpx solid #14532D;
  border-bottom: 5rpx solid #14532D;
  transform: rotate(45deg);
}
.nav-title {
  position: absolute;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 34rpx;
  color: #14532D;
  font-weight: 600;
  letter-spacing: 1rpx;
  pointer-events: none;
}
.nav-right {
  width: 88rpx;
  display: flex;
  justify-content: flex-end;
  margin-left: auto;
  flex-shrink: 0;
}
</style>
