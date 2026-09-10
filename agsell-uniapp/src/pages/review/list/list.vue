<template>
  <view class="review-list-page">
    <NavBar :title="productName || '商品评价'" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="content" @scrolltolower="onReachBottom">
      <!-- 评价概览 -->
      <view class="overview card">
        <view class="overview-left">
          <text class="avg-score">{{ avgScore }}</text>
          <StarRating :rating="Math.round(avgScore)" :readonly="true" size="32rpx" />
          <text class="total-reviews">共 {{ totalReviews }} 条评价</text>
        </view>
        <view class="overview-right">
          <view
            v-for="(ratio, key) in ratingRatios"
            :key="key"
            class="ratio-row"
          >
            <text class="ratio-label">{{ key }}星</text>
            <view class="ratio-bar-bg">
              <view class="ratio-bar" :style="{ width: ratio + '%' }"></view>
            </view>
            <text class="ratio-pct">{{ ratio }}%</text>
          </view>
        </view>
      </view>

      <!-- 评价列表 -->
      <view v-for="review in reviews" :key="review.id" class="review-card card">
        <view class="review-header">
          <!-- 我的评价：显示商品信息 -->
          <template v-if="isMyReviews">
            <image
              class="product-thumb"
              :src="review.productImage || '/static/default-product.png'"
              mode="aspectFill"
              lazy-load
              :alt="review.productName || '商品图片'"
            />
            <view class="product-info">
              <text class="product-name">{{ review.productName }}</text>
              <text v-if="review.specName" class="spec-name">{{ review.specName }}</text>
              <StarRating :rating="review.rating" :readonly="true" size="28rpx" />
            </view>
          </template>
          <!-- 商品评价：显示用户信息 -->
          <template v-else>
            <image
              class="user-avatar"
              :src="review.userAvatar || '/static/default-avatar.png'"
              mode="aspectFill"
              lazy-load
              :alt="review.userName || '用户头像'"
            />
            <view class="user-info">
              <text class="user-name">{{ review.userName }}</text>
              <StarRating :rating="review.rating" :readonly="true" size="28rpx" />
            </view>
          </template>
          <text class="review-time">{{ formatDate(review.createTime) }}</text>
        </view>
        <text class="review-content">{{ review.content }}</text>
        <view v-if="review.images && review.images.length > 0" class="review-images">
          <image
            v-for="(img, i) in review.images"
            :key="i"
            :src="img"
            mode="aspectFill"
            lazy-load
            class="review-img"
            :alt="'评价图片' + (i + 1)"
          />
        </view>
        <view v-if="review.replyContent" class="review-reply">
          <text class="reply-label">商家回复：</text>
          <text class="reply-content">{{ review.replyContent }}</text>
          <text v-if="review.replyTime" class="reply-time">{{ formatDate(review.replyTime) }}</text>
        </view>
      </view>

      <view v-if="reviews.length === 0 && !loading" class="empty">暂无评价</view>
      <view v-if="loading" class="loading">加载中...</view>
      <view v-if="!hasMore && reviews.length > 0" class="no-more">没有更多了</view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../../components/NavBar/NavBar.vue'
import StarRating from '../../../components/StarRating/StarRating.vue'
import { getProductReviews, getMyReviews } from '../../../api/review'
import { formatDate } from '../../../utils/format'

const productId = ref('')
const productName = ref('')
const reviews = ref([])
const totalReviews = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const hasMore = ref(true)

const isMyReviews = computed(() => !productId.value)

const avgScore = computed(() => {
  if (reviews.value.length === 0) return '0.0'
  const sum = reviews.value.reduce((acc, r) => acc + (r.rating || 0), 0)
  return (sum / reviews.value.length).toFixed(1)
})

const ratingRatios = computed(() => {
  const total = reviews.value.length || 1
  const ratios = { 5: 0, 4: 0, 3: 0, 2: 0, 1: 0 }
  reviews.value.forEach(r => {
    if (r.rating && ratios[r.rating] !== undefined) ratios[r.rating]++
  })
  const result = {}
  for (const key in ratios) {
    result[key] = Math.round(ratios[key] / total * 100)
  }
  return result
})

onLoad((options) => {
  productId.value = options.productId || ''
  productName.value = productId.value
    ? decodeURIComponent(options.productName || '商品评价')
    : '我的评价'
  loadReviews()
})

async function loadReviews() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const res = isMyReviews.value
      ? await getMyReviews(pageNum.value, pageSize.value)
      : await getProductReviews(productId.value, pageNum.value, pageSize.value)
    if (res.code === 0) {
      const { records, current, pages } = res.data || {}
      reviews.value = pageNum.value === 1 ? (records || []) : [...reviews.value, ...(records || [])]
      totalReviews.value = res.data?.total || reviews.value.length
      hasMore.value = current < (pages || 1)
    }
  } catch (e) {
    console.error('加载评价失败', e)
  }
  loading.value = false
}

function onReachBottom() {
  if (hasMore.value && !loading.value) {
    pageNum.value++
    loadReviews()
  }
}
</script>

<style scoped>
.review-list-page {
  min-height: 100vh;
  background: #F4F6F5;
}

.content {
  height: 100vh;
  overflow-y: auto;
  padding-bottom: 40rpx;
}

.card {
  background: #FFFFFF;
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.overview {
  display: flex;
  gap: 32rpx;
}

.overview-left {
  text-align: center;
  min-width: 200rpx;
}

.avg-score {
  font-size: 72rpx;
  font-weight: 600;
  color: #A16207;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.total-reviews {
  font-size: 24rpx;
  color: #6B7280;
  margin-top: 12rpx;
}

.overview-right {
  flex: 1;
}

.ratio-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
  font-size: 24rpx;
  color: #6B7280;
}

.ratio-bar-bg {
  flex: 1;
  height: 12rpx;
  background: #F3F5F4;
  border-radius: 6rpx;
  overflow: hidden;
}

.ratio-bar {
  height: 100%;
  background: #A16207;
  border-radius: 6rpx;
}

.ratio-pct {
  font-size: 22rpx;
  color: #6B7280;
  min-width: 48rpx;
  font-variant-numeric: tabular-nums;
}

.review-card {
  padding: 24rpx;
}

.review-header {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.user-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #F3F5F4;
  flex-shrink: 0;
}

.product-thumb {
  width: 96rpx;
  height: 96rpx;
  border-radius: 12rpx;
  background: #F3F5F4;
  flex-shrink: 0;
}

.product-info {
  flex: 1;
  margin-left: 16rpx;
}

.product-name {
  font-size: 28rpx;
  color: #1F2937;
  font-weight: 500;
  display: block;
  margin-bottom: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spec-name {
  font-size: 22rpx;
  color: #9CA3AF;
  display: block;
  margin-bottom: 4rpx;
}

.user-info {
  flex: 1;
  margin-left: 16rpx;
}

.user-name {
  font-size: 26rpx;
  color: #1F2937;
  font-weight: 500;
  display: block;
  margin-bottom: 4rpx;
}

.review-time {
  font-size: 24rpx;
  color: #6B7280;
  flex-shrink: 0;
}

.review-content {
  font-size: 28rpx;
  color: #1F2937;
  line-height: 1.6;
  display: block;
  margin-bottom: 16rpx;
}

.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.review-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  background: #F4F6F5;
}

.review-reply {
  background: #F4F6F5;
  border-radius: 12rpx;
  padding: 16rpx;
}

.reply-label {
  font-size: 24rpx;
  color: #15803D;
  font-weight: 600;
}

.reply-content {
  font-size: 26rpx;
  color: #6B7280;
  line-height: 1.5;
}

.reply-time {
  font-size: 22rpx;
  color: #9CA3AF;
  float: right;
}

.empty, .loading, .no-more {
  text-align: center;
  padding: 60rpx;
  color: #4B5563;
  font-size: 26rpx;
}
</style>
