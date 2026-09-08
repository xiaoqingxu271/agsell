<template>
  <view class="input-entry">
    <text v-if="label" class="input-label">{{ label }}</text>
    <view class="input-wrapper" :class="{ 'is-error': error, 'is-disabled': disabled }">
      <input
        class="input-field"
        :value="modelValue"
        :placeholder="placeholder"
        :placeholder-style="placeholderStyle"
        :type="type"
        :password="type === 'password'"
        :disabled="disabled"
        :maxlength="maxlength"
        @input="handleInput"
        @focus="isFocused = true"
        @blur="isFocused = false"
      />
      <slot name="suffix" />
    </view>
    <view v-if="error" class="input-error">
      <svg class="error-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10"></circle>
        <line x1="12" y1="8" x2="12" y2="12"></line>
        <line x1="12" y1="16" x2="12.01" y2="16"></line>
      </svg>
      <text class="error-text">{{ error }}</text>
    </view>
    <text v-else-if="helperText" class="input-helper">{{ helperText }}</text>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  type: { type: String, default: 'text' },
  error: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  helperText: { type: String, default: '' },
  maxlength: { type: Number, default: 140 }
})
const emit = defineEmits(['update:modelValue', 'focus', 'blur'])

const isFocused = ref(false)
const placeholderStyle = 'color: #9CA3AF; font-size: 28rpx;'

function handleInput(e) {
  emit('update:modelValue', e.detail.value)
}
</script>

<style scoped>
.input-entry {
  width: 100%;
}
.input-label {
  display: block;
  font-size: 28rpx;
  color: #6B7280;
  margin-bottom: 12rpx;
  font-weight: 500;
}
.input-wrapper {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  padding: 0 24rpx;
  background: #FFFFFF;
  border: 1px solid #D1D5DB;
  border-radius: 12rpx;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.input-wrapper:focus-within,
.input-wrapper:focus {
  border-color: #15803D;
  box-shadow: 0 0 0 3px rgba(21, 128, 61, 0.15);
}
.input-wrapper.is-error {
  border-color: #DC2626;
}
.input-wrapper.is-disabled {
  background: #F3F4F6;
  color: #9CA3AF;
}
.input-field {
  flex: 1;
  height: 88rpx;
  font-size: 28rpx;
  color: #1F2937;
}
.input-error {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 8rpx;
}
.error-icon {
  width: 28rpx;
  height: 28rpx;
  color: #DC2626;
  flex-shrink: 0;
}
.error-text {
  font-size: 24rpx;
  color: #DC2626;
  line-height: 1.4;
}
.input-helper {
  display: block;
  font-size: 24rpx;
  color: #6B7280;
  margin-top: 8rpx;
}
</style>
