<template>
  <view class="product-card" @click="$emit('tap', product)">
    <view class="product-image-wrap">
      <image
        class="product-image"
        :src="product.mainImage || '/static/default-product.png'"
        mode="aspectFill"
        lazy-load
        :alt="product.name || '商品图片'"
      />
    </view>
    <view class="product-info">
      <text class="product-name">{{ product.name }}</text>
      <text v-if="product.subtitle" class="product-subtitle">{{ product.subtitle }}</text>
      <view v-if="product.tags && product.tags.length" class="product-tags">
        <text v-for="(tag, i) in product.tags.slice(0, 2)" :key="i" class="product-tag">{{ tag }}</text>
      </view>
      <view class="product-footer">
        <text class="price">¥{{ product.price }}</text>
        <text v-if="product.originalPrice" class="original-price">¥{{ product.originalPrice }}</text>
        <text class="sales">已售{{ product.sales || 0 }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
defineProps({
  product: {
    type: Object,
    default: () => ({})
  }
})
defineEmits(['tap'])
</script>

<style scoped>
.product-card {
  display: flex;
  flex-direction: column;
  background: #FFFFFF;
  border: 1px solid #BBF7D0;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
  min-height: 88rpx;
}

.product-image-wrap {
  width: 100%;
  padding-top: 100%;
  position: relative;
  overflow: hidden;
  background: #F0FDF4;
}

.product-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.product-info {
  padding: 16rpx;
}

.product-name {
  font-size: 28rpx;
  color: #1F2937;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-subtitle {
  font-size: 24rpx;
  color: #6B7280;
  margin-top: 8rpx;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-tags {
  display: flex;
  gap: 8rpx;
  margin-top: 12rpx;
  flex-wrap: wrap;
}

.product-tag {
  font-size: 22rpx;
  color: #14532D;
  background: #DCFCE7;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  line-height: 1.4;
}

.product-footer {
  display: flex;
  align-items: baseline;
  margin-top: 12rpx;
}

.price {
  color: #A16207;
  font-size: 36rpx;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.original-price {
  color: #9CA3AF;
  font-size: 24rpx;
  text-decoration: line-through;
  margin-left: 8rpx;
}

.sales {
  color: #6B7280;
  font-size: 22rpx;
  margin-left: auto;
}
</style>
