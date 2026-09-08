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
          :class="{ selected: selectedId === item.id }"
          @click="$emit('select', item)"
        >
          <view class="address-main">
            <text class="receiver">{{ item.receiver }}</text>
            <text class="phone">{{ item.phone }}</text>
            <text v-if="item.tag" class="tag tag-accent">{{ item.tag }}</text>
            <text v-if="item.isDefault === 1" class="tag tag-success">默认</text>
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
const emit = defineEmits(['close', 'add', 'select', 'maskClick'])

function handleMaskClick() {
  emit('maskClick')
}
</script>

<style scoped>
.address-picker-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.address-picker-content {
  width: 100%;
  background: #FFFFFF;
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
  border-bottom: 1px solid #E5E7EB;
}
.title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}
.cancel-btn {
  font-size: 28rpx;
  color: #6B7280;
  min-width: 88rpx;
}
.add-btn {
  font-size: 28rpx;
  color: #15803D;
  font-weight: 500;
  min-width: 88rpx;
  text-align: right;
}
.address-list {
  max-height: 500rpx;
}
.address-item {
  padding: 24rpx 32rpx;
  border-bottom: 1px solid #E5E7EB;
  min-height: 88rpx;
  border: 1px solid transparent;
  margin: 8rpx 16rpx;
  border-radius: 16rpx;
}
.address-item.selected {
  background: #F0FDF4;
  border-color: #15803D;
}
.address-main {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex-wrap: wrap;
}
.receiver {
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2937;
}
.phone {
  font-size: 28rpx;
  color: #6B7280;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  line-height: 1.4;
}
.tag-accent {
  background: #FFF7ED;
  color: #9A3412;
}
.tag-success {
  background: #DCFCE7;
  color: #166534;
}
.address-detail {
  font-size: 26rpx;
  color: #6B7280;
  margin-top: 12rpx;
  line-height: 1.5;
}
.empty-address {
  text-align: center;
  padding: 60rpx;
  color: #9CA3AF;
  font-size: 28rpx;
}
</style>
