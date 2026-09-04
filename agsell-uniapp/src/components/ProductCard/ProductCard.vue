<template>
  <view class="product-card" @click="$emit('tap', product)">
    <image
      class="product-image"
      :src="product.mainImage || '/static/default-product.png'"
      mode="aspectFill"
      lazy-load
    />
    <view class="product-info">
      <text class="product-name" hover-class="none">{{ product.name }}</text>
      <text class="product-subtitle" hover-class="none">{{ product.subtitle || '' }}</text>
      <view class="product-footer">
        <text class="price">¥{{ product.price }}</text>
        <text v-if="product.originalPrice" class="original-price">¥{{ product.originalPrice }}</text>
        <text class="sales">已售{{ product.sales }}</text>
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
  background: #fff;
  border-radius: 12rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}

.product-image {
  width: 300rpx;
  height: 300rpx;
  background: #f5f5f5;
}

.product-info {
  padding: 16rpx;
}

.product-name {
  font-size: 28rpx;
  color: #333;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-subtitle {
  font-size: 22rpx;
  color: #999;
  margin-top: 8rpx;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-footer {
  display: flex;
  align-items: baseline;
  margin-top: 12rpx;
}

.price {
  color: #FF9800;
  font-size: 32rpx;
  font-weight: bold;
}

.price::before {
  content: '¥';
  font-size: 22rpx;
}

.original-price {
  color: #ccc;
  font-size: 22rpx;
  text-decoration: line-through;
  margin-left: 8rpx;
}

.sales {
  color: #999;
  font-size: 20rpx;
  margin-left: auto;
}
</style>
