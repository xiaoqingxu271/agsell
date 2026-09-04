<template>
  <view class="address-picker-mask" v-if="visible" @click="handleMaskClick">
    <view class="address-picker-content" @click.stop>
      <view class="address-picker-header">
        <text class="cancel-btn" @click="$emit('close')">取消</text>
        <text class="title">选择收货地址</text>
        <text class="add-btn" @click="$emit('add')">新增</text>
      </view>
      <scroll-view scroll-y class="address-list">
        <view
          v-for="(item, index) in addresses"
          :key="item.id"
          class="address-item"
          :class="{ selected: selectedId === item.id, default: item.isDefault === 1 }"
          @click="$emit('select', item)"
        >
          <view class="address-main">
            <text class="receiver">{{ item.receiver }}</text>
            <text class="phone">{{ item.phone }}</text>
            <text v-if="item.tag" class="tag">{{ item.tag }}</text>
            <text v-if="item.isDefault === 1" class="default-tag">默认</text>
          </view>
          <view class="address-detail">{{ item.province }} {{ item.city }} {{ item.district }} {{ item.detail }}</view>
        </view>
        <view v-if="addresses.length === 0" class="empty-address">暂无收货地址</view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  addresses: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null }
})
defineEmits(['close', 'add', 'select', 'maskClick'])

function handleMaskClick() {
  $emit('maskClick')
}
</script>

<style scoped>
.address-picker-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.address-picker-content {
  width: 100%;
  background: #fff;
  border-radius: 32rpx 32rpx 0 0;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}
.address-picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx;
  border-bottom: 1rpx solid #eee;
}
.title {
  font-size: 32rpx;
  font-weight: bold;
  color: #333;
}
.cancel-btn {
  font-size: 28rpx;
  color: #999;
}
.add-btn {
  font-size: 28rpx;
  color: #4CAF50;
}
.address-list {
  max-height: 500rpx;
}
.address-item {
  padding: 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}
.address-item.selected {
  background: #f0fff0;
}
.address-main {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.receiver {
  font-size: 30rpx;
  font-weight: bold;
  color: #333;
}
.phone {
  font-size: 28rpx;
  color: #666;
}
.tag {
  font-size: 20rpx;
  color: #fff;
  background: #4CAF50;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.default-tag {
  font-size: 20rpx;
  color: #fff;
  background: #FF9800;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.address-detail {
  font-size: 26rpx;
  color: #666;
  margin-top: 12rpx;
}
.empty-address {
  text-align: center;
  padding: 60rpx;
  color: #999;
  font-size: 28rpx;
}
</style>
