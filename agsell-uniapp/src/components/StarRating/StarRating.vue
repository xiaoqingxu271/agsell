<template>
  <view class="star-rating" :class="{ 'is-readonly': readonly }">
    <view
      v-for="star in 5"
      :key="star"
      class="star"
      :class="{ active: star <= rating, interactive: !readonly }"
      :aria-label="readonly ? `评分 ${rating} 星` : `点击评 ${star} 星`"
      @click="handleClick(star)"
    >
      <svg class="star-icon" viewBox="0 0 24 24" :fill="star <= rating ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
      </svg>
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
  color: #E5E7EB;
}
.star.interactive {
  cursor: pointer;
}
.star.active {
  color: #A16207;
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
