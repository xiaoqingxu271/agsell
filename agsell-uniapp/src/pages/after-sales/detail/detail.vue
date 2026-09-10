<template>
  <view class="as-detail-page">
    <NavBar title="售后详情" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="content">
      <!-- 状态区 -->
      <view class="status-section">
        <svg class="status-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path :d="statusIconPath"></path>
        </svg>
        <text class="status-text">{{ detail?.statusText }}</text>
        <text class="status-hint">{{ statusHint }}</text>
      </view>

      <!-- 售后信息 -->
      <view class="info-section card">
        <view class="info-row">
          <text class="info-label">售后单号</text>
          <text class="info-value">{{ detail?.afterSalesNo }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">关联订单</text>
          <text class="info-value">{{ detail?.orderNo }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">售后类型</text>
          <text class="info-value">{{ detail?.typeText }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">申请原因</text>
          <text class="info-value">{{ detail?.reasonTypeText }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">申请时间</text>
          <text class="info-value">{{ formatDate(detail?.createTime) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">退款金额</text>
          <text class="info-value pay">¥{{ detail?.refundAmount }}</text>
        </view>
        <view v-if="detail?.handleTime" class="info-row">
          <text class="info-label">处理时间</text>
          <text class="info-value">{{ formatDate(detail?.handleTime) }}</text>
        </view>
      </view>

      <!-- 问题描述 -->
      <view v-if="detail?.reason" class="desc-section card">
        <view class="section-title">问题描述</view>
        <text class="desc-text">{{ detail?.reason }}</text>
      </view>

      <!-- 凭证图片 -->
      <view v-if="detail?.images?.length" class="images-section card">
        <view class="section-title">凭证图片</view>
        <view class="image-grid">
          <image
            v-for="(img, idx) in detail.images"
            :key="img"
            class="image-item"
            :src="img"
            mode="aspectFill"
            :alt="'凭证' + (idx + 1)"
            @click="previewImage(img)"
          />
        </view>
      </view>

      <!-- 处理意见 -->
      <view v-if="detail?.handleRemark" class="remark-section card">
        <view class="section-title">处理意见</view>
        <text class="remark-text">{{ detail?.handleRemark }}</text>
      </view>

      <!-- 底部安全占位：防止底部操作栏遮挡最后内容 -->
      <view class="bottom-space"></view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view v-if="detail && detail.status === 0" class="bottom-bar">
      <view class="action-btn btn-ghost btn-danger" @click="onCancelApply">撤销售后申请</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../../components/NavBar/NavBar.vue'
import { getAfterSalesDetail, cancelAfterSales } from '../../../api/afterSales'
import { formatDate } from '../../../utils/format'

const detail = ref(null)

onLoad(async (options) => {
  const afterSalesNo = options.afterSalesNo
  if (afterSalesNo) await loadDetail(afterSalesNo)
})

async function loadDetail(afterSalesNo) {
  const res = await getAfterSalesDetail(afterSalesNo)
  if (res.code === 0) {
    detail.value = res.data
  }
}

const statusIconPath = computed(() => {
  const status = detail.value?.status
  if (status === 0) {
    // 待处理：时钟
    return 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2'
  }
  if (status === 1) {
    // 已同意：对勾
    return 'M22 11.08V12a10 10 0 1 1-5.93-9.14M22 4 12 14.01l-3-3'
  }
  if (status === 2) {
    // 已拒绝：叉
    return 'M18 6 6 18M6 6l12 12'
  }
  // 已撤销：回退箭头
  return 'M1 4v6h6M3.51 15a9 9 0 1 0 2.13-9.36L1 10'
})

const statusHint = computed(() => {
  const status = detail.value?.status
  const hints = {
    0: '商家正在处理您的申请，请耐心等待',
    1: '退款已同意，款项将按原路退回',
    2: '很抱歉，本次售后申请未通过',
    3: '您已撤销售后申请'
  }
  return hints[status] || ''
})

function previewImage(url) {
  uni.previewImage({ urls: detail.value.images, current: url })
}

function onCancelApply() {
  uni.showModal({
    title: '撤销售后',
    content: '确定要撤销本次售后申请吗？撤销后订单将恢复原状态。',
    success: async (res) => {
      if (res.confirm) {
        const result = await cancelAfterSales(detail.value.afterSalesNo)
        if (result.code === 0) {
          uni.showToast({ title: '已撤销', icon: 'success' })
          loadDetail(detail.value.afterSalesNo)
        } else {
          uni.showToast({ title: result.message || '撤销失败', icon: 'none' })
        }
      }
    }
  })
}
</script>

<style scoped>
.as-detail-page {
  min-height: 100vh;
  background: #F4F6F5;
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
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

/* 状态区：品牌绿渐变（signature，同订单详情） */
.status-section {
  text-align: center;
  margin: 24rpx 24rpx 8rpx;
  padding: 56rpx 24rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #15803D 0%, #14532D 100%);
  box-shadow: 0 12rpx 32rpx rgba(21, 128, 61, 0.25);
}

.status-icon {
  width: 80rpx;
  height: 80rpx;
  color: #FFFFFF;
  display: block;
  margin: 0 auto 16rpx;
}

.status-text {
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
  display: block;
  margin-bottom: 8rpx;
}

.status-hint {
  font-size: 26rpx;
  color: #FFFFFF;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #EEF1EF;
  font-size: 26rpx;
}

.info-row:last-child { border-bottom: none; }

.info-label { color: #6B7280; flex-shrink: 0; margin-right: 24rpx; }
.info-value { color: #1F2937; text-align: right; word-break: break-all; }
.info-value.pay { color: #A16207; font-weight: 600; font-variant-numeric: tabular-nums; }

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #15803D;
  margin-bottom: 16rpx;
  display: block;
}

.desc-text, .remark-text {
  font-size: 28rpx;
  color: #1F2937;
  line-height: 1.6;
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.image-item {
  width: 200rpx;
  height: 200rpx;
  border-radius: 12rpx;
  background: #F4F6F5;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #E3E7E5;
  z-index: 100;
}

.action-btn {
  text-align: center;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
  font-weight: 500;
  box-sizing: border-box;
  min-width: 240rpx;
  padding: 0 40rpx;
}

.btn-ghost {
  background: transparent;
  border: 1rpx solid #15803D;
  color: #15803D;
}

.btn-danger {
  color: #DC2626;
  border-color: #DC2626;
}
</style>
