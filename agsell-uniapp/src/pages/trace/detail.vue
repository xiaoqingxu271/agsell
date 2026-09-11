<template>
  <view class="trace-page">
    <NavBar title="溯源档案" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="trace-scroll">
      <!-- 加载中 -->
      <view v-if="loading" class="state-wrap">
        <text class="state-text">档案加载中...</text>
      </view>

      <!-- 未找到 -->
      <view v-else-if="notFound" class="state-wrap">
        <text class="state-icon">?</text>
        <text class="state-title">未找到溯源档案</text>
        <text class="state-text">批次号不存在或已删除，请核实后重试</text>
      </view>

      <!-- 档案主体 -->
      <template v-else-if="trace">
        <!-- 商品 + 批次号 -->
        <view class="card product-card">
          <view class="product-row">
            <image
              class="product-img"
              :src="trace.productImage || '/static/default-product.png'"
              mode="aspectFill"
              lazy-load
              @click="onPreviewImage(trace.productImage)"
            />
            <view class="product-col">
              <text class="product-name">{{ trace.productName }}</text>
              <view class="batch-row">
                <text class="batch-no">{{ trace.batchNo }}</text>
                <view class="copy-btn" @click="onCopyBatch" role="button" aria-label="复制批次号">复制</view>
              </view>
            </view>
          </view>
        </view>

        <!-- 新鲜度 -->
        <view v-if="trace.freshness" class="card freshness-card" :class="'freshness-' + trace.freshness.level">
          <view class="freshness-left">
            <text class="freshness-level">{{ trace.freshness.levelText }}</text>
            <text class="freshness-desc">{{ freshnessDesc(trace.freshness) }}</text>
          </view>
          <text class="freshness-days">已采摘 {{ trace.freshness.daysSinceHarvest }} 天</text>
        </view>

        <!-- 基本信息 -->
        <view class="card">
          <view class="section-title">产地与质检</view>
          <view class="info-grid">
            <view class="info-item">
              <text class="info-label">种植户</text>
              <text class="info-value">{{ trace.farmerName || '-' }}</text>
            </view>
            <view class="info-item">
              <text class="info-label">联系电话</text>
              <text class="info-value">{{ trace.farmerPhone || '-' }}</text>
            </view>
            <view class="info-item">
              <text class="info-label">产地</text>
              <text class="info-value">{{ trace.origin || '-' }}</text>
            </view>
            <view class="info-item">
              <text class="info-label">认证类型</text>
              <text v-if="trace.certificationType" class="cert-tag">{{ trace.certificationTypeText || trace.certificationType }}</text>
              <text v-else class="info-value">-</text>
            </view>
            <view class="info-item">
              <text class="info-label">种植日期</text>
              <text class="info-value">{{ trace.plantingDate || '-' }}</text>
            </view>
            <view class="info-item">
              <text class="info-label">采摘日期</text>
              <text class="info-value">{{ trace.harvestDate || '-' }}</text>
            </view>
            <view class="info-item info-item-full">
              <text class="info-label">质检结果</text>
              <text class="info-value">{{ trace.qualityCheckResult || '-' }}</text>
            </view>
            <view class="info-item info-item-full">
              <text class="info-label">农残检测</text>
              <text class="info-value">{{ trace.pesticideTest || '-' }}</text>
            </view>
          </view>
        </view>

        <!-- 认证证书 -->
        <view v-if="trace.certificationUrls && trace.certificationUrls.length" class="card">
          <view class="section-title">认证证书</view>
          <view class="cert-grid">
            <image
              v-for="(img, i) in trace.certificationUrls"
              :key="img"
              :src="img"
              mode="aspectFill"
              lazy-load
              class="cert-img"
              @click="onPreviewCert(i)"
            />
          </view>
        </view>

        <!-- 生产记录 -->
        <view class="card">
          <view class="section-title">生产记录（{{ trace.productionRecords.length }}）</view>
          <view v-if="trace.productionRecords.length === 0" class="records-empty">暂无生产记录</view>
          <view v-else class="record-list">
            <view v-for="(record, idx) in trace.productionRecords" :key="record.id" class="record-item">
              <view class="record-timeline">
                <view class="record-dot" :class="{ 'last-dot': idx === trace.productionRecords.length - 1 }" />
                <view v-if="idx !== trace.productionRecords.length - 1" class="record-line" />
              </view>
              <view class="record-body">
                <view class="record-head">
                  <text class="record-type">{{ record.recordTypeText || record.recordType }}</text>
                  <text class="record-date">{{ record.recordDate }}</text>
                </view>
                <text class="record-content">{{ record.content }}</text>
                <view v-if="record.images && record.images.length" class="record-imgs">
                  <image
                    v-for="(img, i) in record.images"
                    :key="img"
                    :src="img"
                    mode="aspectFill"
                    lazy-load
                    class="record-img"
                    @click="onPreviewRecordImgs(record, i)"
                  />
                </view>
                <text v-if="record.operator" class="record-operator">操作人：{{ record.operator }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="footer-tip">本档案由商家录入，供消费者查询产地信息</view>
      </template>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getTraceByBatchNo } from '../../api/trace'

const trace = ref(null)
const loading = ref(true)
const notFound = ref(false)

const FRESHNESS_META = {
  FRESH: { desc: '采摘 3 天内发货，新鲜直达' },
  NORMAL: { desc: '采摘 4-7 天，口感仍佳' },
  FAIR: { desc: '采摘 8-15 天，建议尽快食用' },
  STALE: { desc: '采摘超过 15 天，建议尽快食用' }
}

function freshnessDesc(freshness) {
  return (FRESHNESS_META[freshness.level] || {}).desc || '新鲜度信息'
}

onLoad((options) => {
  const batchNo = String(options?.batchNo || '').trim()
  if (!batchNo) {
    notFound.value = true
    loading.value = false
    return
  }
  loadTrace(batchNo)
})

async function loadTrace(batchNo) {
  loading.value = true
  notFound.value = false
  try {
    const res = await getTraceByBatchNo(batchNo)
    if (res.code === 0 && res.data) {
      trace.value = res.data
    } else {
      notFound.value = true
    }
  } catch (e) {
    console.error('溯源档案加载失败', e)
    notFound.value = true
  } finally {
    loading.value = false
  }
}

function onCopyBatch() {
  uni.setClipboardData({
    data: trace.value?.batchNo || '',
    success: () => uni.showToast({ title: '批次号已复制', icon: 'success' })
  })
}

function onPreviewImage(current) {
  if (!current) return
  uni.previewImage({ urls: [current], current })
}

function onPreviewCert(index) {
  const urls = trace.value?.certificationUrls || []
  uni.previewImage({ urls, current: urls[index] })
}

function onPreviewRecordImgs(record, index) {
  uni.previewImage({ urls: record.images || [], current: (record.images || [])[index] })
}
</script>

<style scoped>
.trace-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F4F6F5;
}

.trace-scroll {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 40rpx;
}

.state-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 48rpx;
}

.state-icon {
  font-size: 72rpx;
  color: #D1D5DB;
  font-weight: 600;
}

.state-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
  margin-top: 24rpx;
}

.state-text {
  font-size: 26rpx;
  color: #9CA3AF;
  margin-top: 12rpx;
}

.card {
  background: #FFFFFF;
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

/* ── 商品卡 ── */
.product-row {
  display: flex;
  align-items: center;
}

.product-img {
  width: 128rpx;
  height: 128rpx;
  border-radius: 16rpx;
  background: #F3F5F4;
  flex-shrink: 0;
}

.product-col {
  margin-left: 24rpx;
  flex: 1;
  min-width: 0;
}

.product-name {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
}

.batch-no {
  font-size: 24rpx;
  color: #6B7280;
  font-family: Consolas, Menlo, monospace;
  letter-spacing: 0.02em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy-btn {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #15803D;
  background: #DCFCE7;
  border-radius: 12rpx;
  padding: 6rpx 18rpx;
}

.copy-btn:active {
  background: #BBF7D0;
}

/* ── 新鲜度卡（按等级着色） ── */
.freshness-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 28rpx;
}

.freshness-left {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.freshness-level {
  font-size: 32rpx;
  font-weight: 700;
}

.freshness-desc {
  font-size: 22rpx;
  color: inherit;
  opacity: 0.85;
}

.freshness-days {
  font-size: 22rpx;
  color: inherit;
  opacity: 0.85;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.freshness-FRESH {
  background: #F0FDF4;
  border-color: #BBF7D0;
  color: #15803D;
}

.freshness-NORMAL {
  background: #EFF6FF;
  border-color: #BFDBFE;
  color: #1D4ED8;
}

.freshness-FAIR {
  background: #FFFBEB;
  border-color: #FDE68A;
  color: #A16207;
}

.freshness-STALE {
  background: #FEF2F2;
  border-color: #FECACA;
  color: #B91C1C;
}

/* ── 信息网格 ── */
.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #15803D;
  margin-bottom: 20rpx;
}

.info-grid {
  display: flex;
  flex-wrap: wrap;
}

.info-item {
  width: 50%;
  box-sizing: border-box;
  padding: 12rpx 16rpx 12rpx 0;
}

.info-item-full {
  width: 100%;
}

.info-label {
  display: block;
  font-size: 22rpx;
  color: #9CA3AF;
  margin-bottom: 6rpx;
}

.info-value {
  font-size: 26rpx;
  color: #1F2937;
  line-height: 1.5;
  word-break: break-all;
}

.cert-tag {
  display: inline-block;
  font-size: 22rpx;
  color: #14532D;
  background: #BBF7D0;
  border-radius: 10rpx;
  padding: 4rpx 16rpx;
  line-height: 1.6;
}

/* ── 认证证书 ── */
.cert-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.cert-img {
  width: 196rpx;
  height: 196rpx;
  border-radius: 12rpx;
  background: #F3F5F4;
  border: 1rpx solid #E5E7EB;
}

/* ── 生产记录时间线 ── */
.records-empty {
  font-size: 26rpx;
  color: #9CA3AF;
  padding: 20rpx 0;
}

.record-list {
  display: flex;
  flex-direction: column;
}

.record-item {
  display: flex;
}

.record-timeline {
  position: relative;
  width: 32rpx;
  flex-shrink: 0;
}

.record-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #15803D;
  margin-top: 10rpx;
}

.record-line {
  position: absolute;
  left: 7rpx;
  top: 30rpx;
  bottom: -4rpx;
  width: 2rpx;
  background: #DCFCE7;
}

.record-dot.last-dot {
  background: #BBF7D0;
}

.record-body {
  flex: 1;
  margin-left: 16rpx;
  padding-bottom: 32rpx;
  min-width: 0;
}

.record-item:last-child .record-body {
  padding-bottom: 0;
}

.record-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.record-type {
  font-size: 26rpx;
  font-weight: 600;
  color: #14532D;
  background: #DCFCE7;
  border-radius: 10rpx;
  padding: 4rpx 16rpx;
}

.record-date {
  font-size: 22rpx;
  color: #9CA3AF;
  flex-shrink: 0;
}

.record-content {
  display: block;
  font-size: 26rpx;
  color: #374151;
  line-height: 1.7;
  margin-top: 12rpx;
  word-break: break-all;
}

.record-imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 12rpx;
}

.record-img {
  width: 128rpx;
  height: 128rpx;
  border-radius: 12rpx;
  background: #F3F5F4;
}

.record-operator {
  display: block;
  font-size: 22rpx;
  color: #9CA3AF;
  margin-top: 10rpx;
}

.footer-tip {
  text-align: center;
  font-size: 22rpx;
  color: #9CA3AF;
  margin: 8rpx 24rpx 0;
}
</style>
