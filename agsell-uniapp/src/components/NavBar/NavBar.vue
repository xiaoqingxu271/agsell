<template>
  <view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="nav-content" :style="{ height: navBarHeight + 'px' }">
      <view v-if="showBack" class="nav-back" @click="$emit('back')">
        <image class="back-icon" src="/static/icon-back.png" mode="aspectFit" />
      </view>
      <text class="nav-title">{{ title }}</text>
      <view class="nav-right"><slot name="right" /></view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onMounted } from 'vue'

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
  background: #4CAF50;
}
.nav-content {
  display: flex;
  align-items: center;
  padding: 0 24rpx;
}
.nav-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon {
  width: 40rpx;
  height: 40rpx;
  filter: brightness(0) invert(1);
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  color: #fff;
  font-weight: 500;
  margin-left: -64rpx;
}
.nav-right {
  width: 64rpx;
}
</style>
