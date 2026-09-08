<template>
  <view class="apply-page">
    <NavBar title="申请售后" />

    <scroll-view scroll-y class="content">
      <!-- 订单信息 -->
      <view v-if="orderDetail" class="order-section card">
        <view class="order-no">订单号：{{ orderDetail.orderNo }}</view>
        <view class="order-amount">
          实付金额
          <text class="order-amount-value">¥{{ orderDetail.payAmount }}</text>
        </view>
      </view>

      <!-- 售后类型 -->
      <view class="section card">
        <view class="section-title">售后类型</view>
        <view class="option-group">
          <view
            v-for="opt in typeOptions"
            :key="opt.value"
            class="option-item"
            :class="{ 'option-active': form.type === opt.value }"
            @click="form.type = opt.value"
            role="button"
          >
            {{ opt.label }}
          </view>
        </view>
      </view>

      <!-- 原因类型 -->
      <view class="section card">
        <view class="section-title">申请原因</view>
        <view class="option-group">
          <view
            v-for="opt in reasonOptions"
            :key="opt.value"
            class="option-item reason-item"
            :class="{ 'option-active': form.reasonType === opt.value }"
            @click="form.reasonType = opt.value"
            role="button"
          >
            {{ opt.label }}
          </view>
        </view>
        <textarea
          class="reason-input"
          v-model="form.reason"
          placeholder="请描述具体问题（选填，最多500字）"
          placeholder-style="color:#9CA3AF"
          maxlength="500"
        />
      </view>

      <!-- 退款金额 -->
      <view class="section card">
        <view class="section-title">退款金额</view>
        <view class="amount-input-row">
          <text class="amount-prefix">¥</text>
          <input
            class="amount-input"
            type="digit"
            v-model="refundAmountText"
            placeholder="请输入退款金额"
            placeholder-style="color:#9CA3AF"
          />
        </view>
        <view class="amount-hint">可退金额上限 ¥{{ orderDetail?.payAmount || '0.00' }}</view>
      </view>

      <!-- 凭证图片 -->
      <view class="section card">
        <view class="section-title">凭证图片（选填）</view>
        <view class="image-grid">
          <view v-for="(img, idx) in images" :key="img" class="image-item">
            <image class="image-preview" :src="img" mode="aspectFill" :alt="`凭证${idx + 1}`" />
            <view class="image-remove" @click="removeImage(idx)" role="button" aria-label="删除图片">×</view>
          </view>
          <view v-if="images.length < 3" class="image-add" @click="chooseImage" role="button">
            <svg class="add-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            <text class="add-text">添加图片</text>
          </view>
        </view>
      </view>

      <!-- 底部安全占位：防止底部提交栏遮挡最后内容 -->
      <view class="bottom-space"></view>
    </scroll-view>

    <!-- 底部提交栏 -->
    <view class="bottom-bar">
      <view class="submit-btn" @click="onSubmit" role="button">提交申请</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../../components/NavBar/NavBar.vue'
import { getOrderDetail } from '../../../api/order'
import { applyAfterSales } from '../../../api/afterSales'
import { uploadMiniImage } from '../../../utils/upload'

const orderDetail = ref(null)
const form = ref({
  orderNo: '',
  type: 1,
  reasonType: 'QUALITY',
  reason: ''
})
const refundAmountText = ref('')
const images = ref([])

const typeOptions = [
  { value: 1, label: '仅退款' },
  { value: 2, label: '退货退款' }
]

const reasonOptions = [
  { value: 'QUALITY', label: '品质问题（坏果包赔）' },
  { value: 'WRONG_ITEM', label: '发错货' },
  { value: 'MISSING_ITEM', label: '少件漏发' },
  { value: 'OTHER', label: '其他原因' }
]

onLoad(async (options) => {
  form.value.orderNo = options.orderNo || ''
  if (form.value.orderNo) {
    const res = await getOrderDetail(form.value.orderNo)
    if (res.code === 0) {
      orderDetail.value = res.data
      refundAmountText.value = res.data.payAmount || '0.00'
    }
  }
})

function chooseImage() {
  uni.chooseImage({
    count: 3 - images.value.length,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      uni.showLoading({ title: '上传中...', mask: true })
      try {
        for (const filePath of res.tempFilePaths) {
          const url = await uploadMiniImage(filePath, 'after-sales')
          images.value.push(url)
        }
      } catch {
        uni.showToast({ title: '图片上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

function removeImage(idx) {
  images.value.splice(idx, 1)
}

async function onSubmit() {
  if (!form.value.orderNo) {
    uni.showToast({ title: '订单号缺失', icon: 'none' })
    return
  }
  const refundAmount = Number(refundAmountText.value)
  if (!refundAmount || refundAmount <= 0) {
    uni.showToast({ title: '请输入有效的退款金额', icon: 'none' })
    return
  }
  if (refundAmount > Number(orderDetail.value?.payAmount || 0)) {
    uni.showToast({ title: '退款金额不能超过实付金额', icon: 'none' })
    return
  }

  const res = await applyAfterSales({
    orderNo: form.value.orderNo,
    type: form.value.type,
    reasonType: form.value.reasonType,
    reason: form.value.reason,
    images: images.value,
    refundAmount
  })
  if (res.code === 0) {
    uni.showToast({ title: '申请成功', icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/after-sales/detail/detail?afterSalesNo=${res.data.afterSalesNo}` })
    }, 800)
  } else {
    uni.showToast({ title: res.message || '申请失败', icon: 'none' })
  }
}
</script>

<style scoped>
.apply-page {
  min-height: 100vh;
  background: #F6F8F7;
  display: flex;
  flex-direction: column;
}

.content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.bottom-space {
  height: 180rpx;
}

.card {
  background: #FFFFFF;
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  border: 1px solid #E5E7EB;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #14532D;
  margin-bottom: 20rpx;
  display: block;
}

.order-no {
  font-size: 26rpx;
  color: #6B7280;
  margin-bottom: 12rpx;
}

.order-amount {
  font-size: 26rpx;
  color: #6B7280;
}

.order-amount-value {
  font-size: 36rpx;
  color: #A16207;
  font-weight: 600;
  margin-left: 12rpx;
  font-variant-numeric: tabular-nums;
}

.option-group {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.option-item {
  padding: 16rpx 28rpx;
  border-radius: 16rpx;
  border: 1px solid #D1D5DB;
  font-size: 26rpx;
  color: #1F2937;
  min-height: 88rpx;
  line-height: 56rpx;
  box-sizing: border-box;
}

.option-active {
  border-color: #15803D;
  color: #15803D;
  background: #F0FDF4;
  font-weight: 600;
}

.reason-item {
  min-width: 200rpx;
  text-align: center;
}

.reason-input {
  width: 100%;
  height: 200rpx;
  margin-top: 24rpx;
  background: #F6F8F7;
  border-radius: 16rpx;
  border: 1px solid #E5E7EB;
  padding: 20rpx;
  font-size: 28rpx;
  color: #1F2937;
  box-sizing: border-box;
}

.amount-input-row {
  display: flex;
  align-items: center;
  background: #F6F8F7;
  border-radius: 16rpx;
  border: 1px solid #E5E7EB;
  padding: 0 24rpx;
  height: 88rpx;
}

.amount-prefix {
  font-size: 32rpx;
  color: #1F2937;
  margin-right: 12rpx;
}

.amount-input {
  flex: 1;
  font-size: 32rpx;
  color: #1F2937;
  height: 88rpx;
  line-height: 88rpx;
}

.amount-hint {
  font-size: 24rpx;
  color: #9CA3AF;
  margin-top: 12rpx;
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.image-item {
  position: relative;
  width: 160rpx;
  height: 160rpx;
}

.image-preview {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  background: #F6F8F7;
}

.image-remove {
  position: absolute;
  top: -12rpx;
  right: -12rpx;
  width: 40rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.6);
  color: #FFFFFF;
  border-radius: 50%;
  font-size: 28rpx;
}

.image-add {
  width: 160rpx;
  height: 160rpx;
  border: 1px dashed #D1D5DB;
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #F6F8F7;
}

.add-icon {
  width: 48rpx;
  height: 48rpx;
  color: #6B7280;
}

.add-text {
  font-size: 24rpx;
  color: #6B7280;
  margin-top: 8rpx;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1px solid #E5E7EB;
  z-index: 100;
}

.submit-btn {
  background: #15803D;
  color: #FFFFFF;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
  text-align: center;
}
</style>
