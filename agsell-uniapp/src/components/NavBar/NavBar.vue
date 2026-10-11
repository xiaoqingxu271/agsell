<template>
  <view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px', background: background }">
    <view
      class="nav-content"
      :style="{ height: navBarHeight + 'px', borderBottom: bordered ? '1rpx solid #EDEEF0' : 'none' }"
    >
      <view v-if="showBack" class="nav-back" aria-label="返回" @click="$emit('back')">
        <view class="nav-back-arrow" :style="{ borderLeftColor: color, borderBottomColor: color }"></view>
      </view>
      <text class="nav-title" :style="{ color: color }">{{ title }}</text>
      <view class="nav-right"><slot name="right" /></view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  showBack: { type: Boolean, default: true },
  // 可选主题化：默认值完全保持原有白色导航外观，仅在秒杀等频道页传入红色参数
  background: { type: String, default: '#FFFFFF' },
  color: { type: String, default: '#1A1A1A' },
  bordered: { type: Boolean, default: true }
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
  border-left: 5rpx solid #1A1A1A;
  border-bottom: 5rpx solid #1A1A1A;
  transform: rotate(45deg);
}
.nav-title {
  position: absolute;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 34rpx;
  color: #1A1A1A;
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
