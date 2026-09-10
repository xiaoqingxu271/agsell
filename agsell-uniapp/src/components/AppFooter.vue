<template>
  <view class="app-footer">
    <view class="footer-content">
      <slot name="left">
        <view v-if="showTotal" class="footer-total">
          <text class="total-label">合计：</text>
          <text class="total-amount">¥{{ totalAmount }}</text>
        </view>
      </slot>
      <view class="footer-actions">
        <slot name="actions">
          <button
            class="footer-btn btn-ghost"
            v-if="secondaryText"
            :disabled="secondaryDisabled"
            @click="$emit('secondary')"
          >
            {{ secondaryText }}
          </button>
          <button
            class="footer-btn btn-primary"
            :disabled="disabled"
            @click="$emit('submit')"
          >
            {{ buttonText }}
          </button>
        </slot>
      </view>
    </view>
  </view>
</template>

<script setup>
defineProps({
  buttonText: { type: String, default: '提交' },
  secondaryText: { type: String, default: '' },
  totalAmount: { type: [String, Number], default: '0.00' },
  showTotal: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  secondaryDisabled: { type: Boolean, default: false }
})
defineEmits(['submit', 'secondary'])
</script>

<style scoped>
.app-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #FFFFFF;
  border-top: 1rpx solid #E3E7E5;
  padding-bottom: env(safe-area-inset-bottom);
  z-index: 100;
}
.footer-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  min-height: 100rpx;
}
.footer-total {
  display: flex;
  align-items: baseline;
}
.total-label {
  font-size: 28rpx;
  color: #1F2937;
}
.total-amount {
  font-size: 40rpx;
  font-weight: 600;
  color: #A16207;
  font-variant-numeric: tabular-nums;
}
.footer-actions {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.footer-btn {
  min-width: 200rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
  padding: 0 32rpx;
  margin: 0;
}
.footer-btn::after {
  border: none;
}
.btn-primary {
  background: #15803D;
  color: #FFFFFF;
  border: none;
}
.btn-primary[disabled] {
  background: #E5E7EB;
  color: #9CA3AF;
}
.btn-ghost {
  background: transparent;
  color: #15803D;
  border: 1px solid #15803D;
}
</style>
