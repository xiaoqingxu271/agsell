<template>
  <view class="star-rating">
    <view
      v-for="star in 5"
      :key="star"
      class="star"
      :class="{ active: star <= rating }"
      @click="$emit('change', star)"
    >
      <text class="star-icon">{{ star <= rating ? '★' : '☆' }}</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  rating: { type: Number, default: 0 }
})
defineEmits(['change'])
</script>

<style scoped>
.star-rating {
  display: flex;
  justify-content: center;
  gap: 16rpx;
}
.star {
  padding: 8rpx;
}
.star-icon {
  font-size: 48rpx;
  color: #ddd;
}
.star.active .star-icon {
  color: #FFC107;
}
</style>
