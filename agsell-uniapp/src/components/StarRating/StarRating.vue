<template>
  <view class="star-rating" :class="{ 'is-readonly': readonly }">
    <view
      v-for="star in 5"
      :key="star"
      class="star"
      :class="{ active: star <= rating, interactive: !readonly }"
      @click="handleClick(star)"
    >
      <image
        class="star-icon"
        :src="star <= rating ? '/static/icon-star-filled.png' : '/static/icon-star-empty.png'"
        mode="aspectFit"
      />
    </view>
    <text v-if="showValue" class="star-value">{{ rating.toFixed(1) }}</text>
  </view>
</template>

<script setup>
const props = defineProps({
  rating: { type: Number, default: 0 },
  readonly: { type: Boolean, default: false },
  showValue: { type: Boolean, default: false },
  size: { type: String, default: '40rpx' }
})
const emit = defineEmits(['change'])

function handleClick(star) {
  if (props.readonly) return
  emit('change', star)
}
</script>

<style scoped>
.star-rating {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.star {
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.star.interactive {
  cursor: pointer;
}
.star-icon {
  width: v-bind(size);
  height: v-bind(size);
}
.star-value {
  font-size: 24rpx;
  color: #6B7280;
  margin-left: 8rpx;
  font-variant-numeric: tabular-nums;
}
</style>
