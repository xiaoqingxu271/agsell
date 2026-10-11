<template>
  <view class="trace-page">
    <NavBar title="溯源档案" background="#00A66A" color="#FFFFFF" :bordered="false" @back="uni.navigateBack()" />

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
        <!-- 溯源结果头（服务绿大底） -->
        <view class="trhero">
          <view class="trhero-top">
            <text class="trhero-label">溯源码</text>
            <view v-if="trace.freshness" class="trhero-ok">
              <wd-icon name="check-circle" size="24rpx" custom-class="trhero-ok-icon" />
              <text class="trhero-ok-text">{{ trace.freshness.levelText }} · 已核验</text>
            </view>
          </view>
          <text class="trhero-title">{{ trace.productName }}</text>
          <view class="trhero-code">
            <text class="trhero-code-no">{{ trace.batchNo }}</text>
            <text v-if="trace.origin" class="trhero-code-origin"> · {{ trace.origin }}</text>
          </view>
        </view>

        <!-- 商品 + 批次号（负边距上提压在 hero 上） -->
        <view class="trcard">
          <image
            class="trcard-img"
            :src="trace.productImage || '/static/default-product.png'"
            mode="aspectFill"
            lazy-load
            @click="onPreviewImage(trace.productImage)"
          />
          <view class="trcard-col">
            <text class="trcard-name">{{ trace.productName }}</text>
            <text v-if="trace.freshness" class="trcard-meta">{{ freshnessDesc(trace.freshness) }}</text>
            <view class="batch-row">
              <text class="batch-no">{{ trace.batchNo }}</text>
              <view class="copy-btn" @click="onCopyBatch" role="button" aria-label="复制批次号">复制</view>
            </view>
          </view>
        </view>

        <!-- 生产记录（全链路时间轴） -->
        <view class="tcard tl-card">
          <view class="section-title">全链路追溯（{{ trace.productionRecords.length }}）</view>
          <view v-if="trace.productionRecords.length === 0" class="records-empty">暂无生产记录</view>
          <view v-else class="tl">
            <view
              v-for="(record, idx) in trace.productionRecords"
              :key="record.id"
              class="tl-node"
              :class="{ 'is-cur': idx === trace.productionRecords.length - 1 }"
            >
              <view class="tl-rail">
                <view class="tl-dot" />
                <view v-if="idx !== trace.productionRecords.length - 1" class="tl-line" />
              </view>
              <view class="tl-body">
                <view class="tl-head">
                  <text class="tl-type">{{ record.recordTypeText || record.recordType }}</text>
                  <text v-if="idx !== trace.productionRecords.length - 1" class="tl-badge">已完成</text>
                </view>
                <text class="tl-content">{{ record.content }}</text>
                <view v-if="record.images && record.images.length" class="tl-imgs">
                  <image
                    v-for="(img, i) in record.images"
                    :key="img"
                    :src="img"
                    mode="aspectFill"
                    lazy-load
                    class="tl-img"
                    @click="onPreviewRecordImgs(record, i)"
                  />
                </view>
                <text class="tl-date">{{ record.recordDate }}{{ record.operator ? ' · ' + record.operator : '' }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 质检与认证 -->
        <view class="tcard">
          <view class="section-title">质检与认证</view>
          <view v-if="trace.certificationUrls && trace.certificationUrls.length" class="cert-grid">
            <view v-for="(img, i) in trace.certificationUrls" :key="img" class="cert-card" @click="onPreviewCert(i)" role="button" aria-label="认证证书图片">
              <image class="cert-img" :src="img" mode="aspectFill" lazy-load />
            </view>
          </view>
          <view v-else class="cert-grid">
            <view class="cert-card">
              <view class="cert-ph"><wd-icon name="list" size="60rpx" custom-class="cert-ph-ic" /></view>
              <text class="cert-nm">农残检测报告</text>
              <text class="cert-no">{{ trace.pesticideTest || '暂无' }}</text>
            </view>
            <view class="cert-card">
              <view class="cert-ph"><wd-icon name="secured" size="60rpx" custom-class="cert-ph-ic" /></view>
              <text class="cert-nm">质检结论</text>
              <text class="cert-no">{{ trace.qualityCheckResult || '暂无' }}</text>
            </view>
            <view class="cert-card">
              <view class="cert-ph"><wd-icon name="goods" size="60rpx" custom-class="cert-ph-ic" /></view>
              <text class="cert-nm">认证类型</text>
              <text class="cert-no">{{ trace.certificationTypeText || trace.certificationType || '未认证' }}</text>
            </view>
          </view>
          <view class="info-rows">
            <view class="info-row"><text class="info-row-k">种植日期</text><text class="info-row-v">{{ trace.plantingDate || '-' }}</text></view>
            <view class="info-row"><text class="info-row-k">采摘日期</text><text class="info-row-v">{{ trace.harvestDate || '-' }}</text></view>
          </view>
        </view>

        <!-- 产地档案 -->
        <view class="tcard tcard--flush">
          <view class="trmap">
            <view class="trmap-pin"><view class="trmap-pin-dot" /></view>
            <text class="trmap-tip">{{ trace.origin || '产地信息待完善' }}</text>
          </view>
          <view class="info-rows">
            <view class="info-row"><text class="info-row-k">产地</text><text class="info-row-v">{{ trace.origin || '-' }}</text></view>
            <view class="info-row"><text class="info-row-k">种植户</text><text class="info-row-v">{{ trace.farmerName || '-' }}</text></view>
            <view class="info-row"><text class="info-row-k">联系电话</text><text class="info-row-v">{{ trace.farmerPhone || '-' }}</text></view>
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

<style lang="scss" scoped>
.trace-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $uni-bg-color-grey;
}

.trace-scroll {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 40rpx;
}

/* ── 加载 / 空状态 ── */
.state-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 48rpx;
}

.state-icon {
  font-size: 72rpx;
  color: $uni-border-color-strong;
  font-weight: 600;
}

.state-title {
  font-size: 32rpx;
  font-weight: 600;
  color: $uni-text-color;
  margin-top: 24rpx;
}

.state-text {
  font-size: 26rpx;
  color: $uni-text-color-placeholder;
  margin-top: 12rpx;
}

/* ── 溯源结果头（服务绿大底） ── */
.trhero {
  background: linear-gradient(160deg, $uni-color-service, $uni-color-service-dark);
  padding: 40rpx 28rpx 80rpx;
  color: $uni-text-color-inverse;
}

.trhero-top {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.trhero-label {
  font-size: 24rpx;
  opacity: 0.85;
}

.trhero-ok {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  background: rgba(255, 255, 255, 0.18);
  padding: 6rpx 18rpx;
  border-radius: 22rpx;
}

:deep(.trhero-ok-icon) {
  color: $uni-text-color-inverse;
}

.trhero-ok-text {
  color: $uni-text-color-inverse;
}

.trhero-title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  margin-top: 24rpx;
  line-height: 1.3;
}

.trhero-code {
  display: flex;
  align-items: center;
  margin-top: 10rpx;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  font-size: 24rpx;
  opacity: 0.85;
  letter-spacing: 1rpx;
}

/* ── 商品卡（负边距上提压在 hero 上） ── */
.trcard {
  margin: -60rpx 24rpx 0;
  background: $uni-bg-color;
  border-radius: $uni-border-radius-base;
  box-shadow: $uni-shadow-lg;
  padding: 24rpx;
  display: flex;
  gap: 20rpx;
  position: relative;
}

.trcard-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: $uni-border-radius-sm;
  background: $uni-bg-color-img;
  flex-shrink: 0;
}

.trcard-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.trcard-name {
  font-size: 26rpx;
  font-weight: 600;
  color: $uni-text-color;
  line-height: 38rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.trcard-meta {
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
  margin-top: 8rpx;
  line-height: 32rpx;
}

.batch-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: auto;
  padding-top: 12rpx;
}

.batch-no {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy-btn {
  flex-shrink: 0;
  font-size: 22rpx;
  color: $uni-color-service-dark;
  background: $uni-color-service-50;
  border-radius: $uni-border-radius-sm;
  padding: 6rpx 18rpx;
}

/* ── 通用白卡 ── */
.tcard {
  background: $uni-bg-color;
  margin: 16rpx 24rpx 0;
  padding: 24rpx;
  border-radius: $uni-border-radius-base;
  box-shadow: $uni-shadow-sm;
}

.tcard--flush {
  padding: 0;
  overflow: hidden;
}

.tcard--flush .info-rows {
  padding: 20rpx 24rpx;
}

.tl-card {
  padding: 28rpx 24rpx 12rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $uni-text-color;
  margin-bottom: 20rpx;
}

/* ── 键值行（产地 / 质检等） ── */
.info-rows {
  padding: 4rpx 0;
}

.info-row {
  display: flex;
  font-size: 22rpx;
  color: $uni-text-color-grey;
  line-height: 40rpx;
}

.info-row-k {
  width: 112rpx;
  flex: none;
  color: $uni-text-color-placeholder;
}

.info-row-v {
  flex: 1;
  min-width: 0;
  word-break: break-all;
}

/* ── 质检与认证卡 ── */
.cert-grid {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.cert-card {
  flex: 1;
  min-width: 0;
  border: 2rpx solid $uni-border-color;
  border-radius: 12rpx;
  overflow: hidden;
  padding-bottom: 12rpx;
}

.cert-ph {
  height: 156rpx;
  background: #FAFBFC;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.cert-ph-ic) {
  color: $uni-text-color-disable;
}

.cert-img {
  width: 100%;
  height: 156rpx;
  display: block;
  background: $uni-bg-color-img;
}

.cert-nm {
  display: block;
  padding: 12rpx 16rpx 0;
  font-size: 22rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.cert-no {
  display: block;
  padding: 0 16rpx;
  margin-top: 4rpx;
  font-size: 18rpx;
  color: $uni-text-color-placeholder;
  line-height: 1.4;
}

/* ── 全链路时间轴 ── */
.records-empty {
  font-size: 26rpx;
  color: $uni-text-color-placeholder;
  padding: 20rpx 0;
}

.tl {
  display: flex;
  flex-direction: column;
}

.tl-node {
  display: flex;
}

.tl-rail {
  position: relative;
  width: 32rpx;
  flex-shrink: 0;
}

.tl-dot {
  width: 22rpx;
  height: 22rpx;
  border-radius: $uni-border-radius-circle;
  background: $uni-color-service;
  margin-top: 6rpx;
  margin-left: 2rpx;
}

.tl-node.is-cur .tl-dot {
  background: $uni-bg-color;
  border: 4rpx solid $uni-color-primary;
  box-shadow: 0 0 0 6rpx $uni-color-primary-50;
  width: 18rpx;
  height: 18rpx;
}

.tl-line {
  position: absolute;
  left: 12rpx;
  top: 30rpx;
  bottom: -4rpx;
  width: 2rpx;
  background: $uni-border-color;
}

.tl-body {
  flex: 1;
  margin-left: 20rpx;
  padding-bottom: 32rpx;
  min-width: 0;
}

.tl-node:last-child .tl-body {
  padding-bottom: 0;
}

.tl-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.tl-type {
  font-size: 26rpx;
  color: $uni-text-color;
}

.tl-node.is-cur .tl-type {
  font-weight: 600;
  color: $uni-color-primary;
}

.tl-badge {
  font-size: 18rpx;
  color: $uni-color-service;
  border: 2rpx solid $uni-color-service-100;
  border-radius: 4rpx;
  padding: 0 8rpx;
  line-height: 26rpx;
}

.tl-date {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
}

.tl-content {
  display: block;
  font-size: 22rpx;
  color: $uni-text-color-grey;
  line-height: 32rpx;
  margin-top: 6rpx;
  word-break: break-all;
}

.tl-imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 12rpx;
}

.tl-img {
  width: 128rpx;
  height: 128rpx;
  border-radius: $uni-border-radius-sm;
  background: $uni-bg-color-img;
}

/* ── 产地示意（网格底 + 品牌色定位针） ── */
.trmap {
  position: relative;
  height: 236rpx;
  background:
    repeating-linear-gradient(0deg, #EEF1F3, #EEF1F3 2rpx, transparent 2rpx, transparent 44rpx),
    repeating-linear-gradient(90deg, #EEF1F3, #EEF1F3 2rpx, transparent 2rpx, transparent 44rpx),
    #F7F8FA;
  display: flex;
  align-items: center;
  justify-content: center;
}

.trmap-pin {
  position: absolute;
  left: 50%;
  top: 46%;
  width: 52rpx;
  height: 52rpx;
  transform: translate(-50%, -100%) rotate(-45deg);
  background: $uni-color-primary;
  border-radius: 50% 50% 50% 0;
  box-shadow: 0 6rpx 16rpx rgba(225, 37, 27, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.trmap-pin-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $uni-bg-color;
}

.trmap-tip {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 16rpx;
  text-align: center;
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
}

.footer-tip {
  text-align: center;
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
  margin: 24rpx 24rpx 0;
}
</style>
