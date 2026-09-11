<template>
  <view class="product-page">
    <NavBar title="商品详情" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="product-scroll">
      <!-- 商品图片轮播 -->
      <swiper class="product-swiper" indicator-dots autoplay circular interval="3000">
        <swiper-item v-for="(img, i) in images" :key="i">
          <image :src="img" mode="aspectFill" lazy-load class="swiper-image" :alt="product.name || '商品图片'" @click="onPreviewImage(img)" />
        </swiper-item>
        <swiper-item v-if="images.length === 0">
          <image :src="product.mainImage || '/static/default-product.png'" mode="aspectFill" lazy-load class="swiper-image" :alt="product.name || '商品图片'" @click="onPreviewImage(product.mainImage)" />
        </swiper-item>
      </swiper>

      <!-- 基本信息 -->
      <view class="info-section card">
        <view class="price-row">
          <text class="price">¥{{ product.price }}</text>
          <text v-if="product.originalPrice" class="original-price">¥{{ product.originalPrice }}</text>
        </view>
        <view class="name">{{ product.name }}</view>
        <view v-if="product.subtitle" class="subtitle">{{ product.subtitle }}</view>
        <view class="meta-row">
          <text v-if="product.categoryName" class="meta-tag tag-primary">{{ product.categoryName }}</text>
          <text v-if="product.parentCategoryName" class="meta-tag tag-info">{{ product.parentCategoryName }}</text>
          <text class="meta-text">销量 {{ product.sales || 0 }}</text>
          <text class="meta-text">库存 {{ product.stock || 0 }}</text>
        </view>
        <!-- 产地信息 -->
        <view v-if="product.origin || product.harvestDate" class="origin-info">
          <text v-if="product.origin" class="origin-item">产地：{{ product.origin }}</text>
          <text v-if="product.harvestDate" class="origin-item">采摘：{{ product.harvestDate }}</text>
          <text v-if="product.shelfLife" class="origin-item">保质期：{{ product.shelfLife }}</text>
          <text v-if="product.storage" class="origin-item">储存：{{ product.storage }}</text>
        </view>

        <!-- 溯源入口 -->
        <view v-if="product.hasTrace" class="trace-entry" @click="onViewTrace" role="button">
          <view class="trace-entry-left">
            <view class="trace-entry-icon" aria-hidden="true"></view>
            <view class="trace-entry-col">
              <text class="trace-entry-title">产地溯源档案</text>
              <text class="trace-entry-batch">{{ product.traceBatchNo }}</text>
            </view>
          </view>
          <view class="trace-entry-arrow" aria-hidden="true">›</view>
        </view>
      </view>

      <!-- 规格选择 -->
      <view v-if="specs && specs.length > 0" class="spec-section card">
        <view class="section-title">选择规格</view>
        <view class="spec-list">
          <view
            v-for="spec in specs"
            :key="spec.id"
            class="spec-item"
            :class="{ active: selectedSpec && selectedSpec.id === spec.id }"
            @click="onSpecTap(spec)"
            role="button"
          >
            <text class="spec-name">{{ spec.specName }}</text>
            <text class="spec-price">¥{{ spec.price }}</text>
            <text class="spec-stock">库存{{ spec.stock }}</text>
          </view>
        </view>
      </view>

      <!-- 数量选择 -->
      <view class="quantity-section card">
        <view class="section-title">数量</view>
        <view class="quantity-row">
          <view class="qty-btn" @click="changeQty(-1)" role="button" aria-label="减少数量">-</view>
          <text class="qty-value">{{ quantity }}</text>
          <view class="qty-btn" @click="changeQty(1)" role="button" aria-label="增加数量">+</view>
          <text class="qty-stock">库存{{ stock }}</text>
        </view>
      </view>

      <!-- 商品详情 -->
      <view v-if="product.description" class="detail-section card">
        <view class="section-title">商品详情</view>
        <view class="detail-content" v-html="product.description"></view>
      </view>

      <!-- 评价摘要 -->
      <view class="review-preview card">
        <view class="review-title-row">
          <view class="section-title">用户评价</view>
          <text class="review-more" @click="onViewReviews">查看全部评价</text>
        </view>
        <view v-if="reviews.length > 0" class="review-list">
          <view v-for="r in reviews.slice(0, 2)" :key="r.id" class="review-card-item">
            <view class="review-user-row">
              <image class="review-avatar" :src="r.userAvatar || '/static/default-avatar.png'" mode="aspectFill" lazy-load />
              <view class="review-user-col">
                <text class="review-username">{{ r.userName || '匿名用户' }}</text>
                <StarRating :rating="r.rating || 5" :readonly="true" size="24rpx" />
              </view>
            </view>
            <text class="review-content-text">{{ r.content }}</text>
            <view class="review-card-footer">
              <text class="review-date-text">{{ formatDate(r.createTime) }}</text>
              <view class="review-like-btn">
                <view class="like-thumb"></view>
                <text class="like-num">{{ r.likeCount || 0 }}</text>
              </view>
            </view>
          </view>
        </view>
        <text v-else class="review-empty">暂无评价</text>
      </view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="bottom-right">
        <view class="btn-cart" @click="onAddToCart">加入购物车</view>
        <view class="btn-buy" @click="onBuyNow">立即购买</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import StarRating from '../../components/StarRating/StarRating.vue'
import { getProductDetail, getHotProducts } from '../../api/product'
import { getProductReviews } from '../../api/review'
import { addToCart } from '../../api/cart'
import { wxLogin, isLoggedIn } from '../../utils/request'

const product = ref({})
const images = ref([])
const specs = ref([])
const selectedSpec = ref(null)
const quantity = ref(1)
const reviews = ref([])
const relatedProducts = ref([])

const allImages = computed(() => {
  if (images.value && images.value.length > 0) {
    return images.value
  }
  if (product.value.mainImage) {
    return [product.value.mainImage]
  }
  return []
})

function onPreviewImage(current) {
  const urls = allImages.value
  if (urls.length === 0) return
  uni.previewImage({
    urls,
    current: current || urls[0]
  })
}

onLoad((options) => {
  const id = String(options?.id || '')
  if (id && id !== 'undefined' && id !== 'null') {
    loadProductDetail(id)
    loadReviews(id)
    loadRelatedProducts(id)
  } else {
    uni.showToast({ title: '商品ID无效', icon: 'none' })
  }
})

async function loadProductDetail(id) {
  if (!id) return
  try {
    const res = await getProductDetail(id)
    if (res.code === 0 && res.data) {
      product.value = res.data
      if (res.data.images) {
        images.value = Array.isArray(res.data.images) ? res.data.images : []
      }
      if (res.data.specs && res.data.specs.length > 0) {
        specs.value = res.data.specs
        selectedSpec.value = res.data.specs[0]
      }
      quantity.value = 1
    } else {
      uni.showToast({ title: res.message || '商品加载失败', icon: 'none' })
    }
  } catch (e) {
    console.error('商品详情加载失败', e)
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
  }
}

async function loadReviews(productId) {
  const res = await getProductReviews(productId, 1, 3)
  if (res.code === 0) {
    reviews.value = res.data?.records || []
  }
}

async function loadRelatedProducts(currentId) {
  const res = await getHotProducts(6)
  if (res.code === 0) {
    relatedProducts.value = res.data.filter(p => p.id !== currentId)
  }
}

function onSpecTap(spec) {
  selectedSpec.value = spec
  quantity.value = 1
}

function changeQty(delta) {
  const maxStock = selectedSpec.value?.stock || product.value.stock || 999
  quantity.value = Math.max(1, Math.min(quantity.value + delta, maxStock))
}

async function onAddToCart() {
  if (!isLoggedIn()) {
    const success = await wxLogin()
    if (!success) return
  }
  const productId = product.value.id
  const specId = selectedSpec.value?.id || null
  const res = await addToCart({ productId, specId, quantity: quantity.value })
  if (res.code === 0) {
    uni.showToast({ title: '已加入购物车', icon: 'success' })
  } else {
    uni.showToast({ title: res.message || '操作失败', icon: 'none' })
  }
}

function onBuyNow() {
  const productId = product.value.id
  const specId = selectedSpec.value?.id || null
  uni.navigateTo({
    url: `/pages/order/confirm/confirm?productId=${productId}&specId=${specId}&quantity=${quantity.value}`
  })
}

function onViewReviews() {
  uni.navigateTo({
    url: `/pages/review/list/list?productId=${product.value.id}&productName=${encodeURIComponent(product.value.name)}`
  })
}

function onViewTrace() {
  if (!product.value.traceBatchNo) {
    uni.showToast({ title: '溯源信息暂不可用', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/pages/trace/detail?batchNo=${encodeURIComponent(product.value.traceBatchNo)}`
  })
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  // 取日期部分，处理 "2026-09-09T08:07:08" 或 "2026-09-09 08:07:08" 格式
  const datePart = String(dateStr).split('T')[0].split(' ')[0]
  const parts = datePart.split('-')
  if (parts.length === 3) {
    return `${parseInt(parts[0])}年${parseInt(parts[1])}月${parseInt(parts[2])}日`
  }
  return dateStr
}

const stock = computed(() => selectedSpec.value?.stock ?? product.value.stock ?? 0)
</script>

<style scoped>
.product-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F4F6F5;
}

.product-scroll {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 200rpx;
}

.product-swiper {
  width: 100%;
  height: 750rpx;
}

.swiper-image {
  width: 100%;
  height: 750rpx;
  border-radius: 12rpx;
  background: #FFFFFF;
}

.card {
  background: #FFFFFF;
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.info-section .price-row {
  display: flex;
  align-items: baseline;
}

.price {
  font-size: 48rpx;
  color: #A16207;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.original-price {
  font-size: 28rpx;
  color: #9CA3AF;
  text-decoration: line-through;
  margin-left: 16rpx;
}

.name {
  font-size: 36rpx;
  font-weight: 600;
  color: #1F2937;
  margin-top: 16rpx;
  line-height: 1.4;
}

.subtitle {
  font-size: 26rpx;
  color: #6B7280;
  margin-top: 8rpx;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
}

.meta-tag {
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 12rpx;
  line-height: 1.4;
}

.tag-primary {
  background: #BBF7D0;
  color: #14532D;
}

.tag-info {
  background: #F3F5F4;
  color: #4B5563;
}

.meta-text {
  font-size: 24rpx;
  color: #6B7280;
}

.origin-info {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}

.origin-item {
  font-size: 24rpx;
  color: #6B7280;
}

/* ── 溯源入口 ── */
.trace-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  background: #F0FDF4;
  border: 1rpx solid #BBF7D0;
  border-radius: 16rpx;
}

.trace-entry:active {
  background: #DCFCE7;
}

.trace-entry-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-width: 0;
  flex: 1;
}

.trace-entry-icon {
  width: 44rpx;
  height: 44rpx;
  border-radius: 12rpx;
  background: #15803D;
  position: relative;
  flex-shrink: 0;
}

/* 二维码角点示意（纯 CSS，无 emoji） */
.trace-entry-icon::before {
  content: '';
  position: absolute;
  left: 8rpx;
  top: 8rpx;
  width: 12rpx;
  height: 12rpx;
  border: 3rpx solid #FFFFFF;
  border-right: none;
  border-bottom: none;
  border-top-left-radius: 4rpx;
}

.trace-entry-icon::after {
  content: '';
  position: absolute;
  right: 8rpx;
  bottom: 8rpx;
  width: 12rpx;
  height: 12rpx;
  border: 3rpx solid #FFFFFF;
  border-left: none;
  border-top: none;
  border-bottom-right-radius: 4rpx;
}

.trace-entry-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.trace-entry-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #14532D;
}

.trace-entry-batch {
  font-size: 22rpx;
  color: #15803D;
  font-family: Consolas, Menlo, monospace;
  margin-top: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-entry-arrow {
  font-size: 36rpx;
  color: #15803D;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #15803D;
  margin-bottom: 20rpx;
}

.spec-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.spec-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20rpx 24rpx;
  border: 1rpx solid #D1D5DB;
  border-radius: 16rpx;
  min-width: 180rpx;
  min-height: 88rpx;
  box-sizing: border-box;
  transition: border-color 150ms ease-out, background 150ms ease-out;
}

.spec-item.active {
  border-color: #15803D;
  background: #F0FDF4;
}

.spec-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2937;
}

.spec-price {
  font-size: 26rpx;
  color: #A16207;
  margin-top: 8rpx;
  font-variant-numeric: tabular-nums;
}

.spec-stock {
  font-size: 22rpx;
  color: #6B7280;
  margin-top: 4rpx;
}

.quantity-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.qty-btn {
  width: 64rpx;
  height: 64rpx;
  border: 1rpx solid #D1D5DB;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #1F2937;
  background: #FFFFFF;
}

.qty-btn:active {
  background: #F4F9F5;
}

.qty-value {
  font-size: 32rpx;
  min-width: 60rpx;
  text-align: center;
  color: #1F2937;
  font-variant-numeric: tabular-nums;
}

.qty-stock {
  font-size: 24rpx;
  color: #6B7280;
  margin-left: auto;
}

.detail-content {
  font-size: 28rpx;
  color: #1F2937;
  line-height: 1.8;
}

.review-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.review-card-item {
  padding-bottom: 24rpx;
  border-bottom: 1rpx solid #EEF1EF;
}

.review-card-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.review-user-row {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.review-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #F3F5F4;
  flex-shrink: 0;
}

.review-user-col {
  margin-left: 16rpx;
  flex: 1;
}

.review-username {
  display: block;
  font-size: 26rpx;
  color: #1F2937;
  font-weight: 500;
  margin-bottom: 6rpx;
}

.review-content-text {
  display: block;
  font-size: 26rpx;
  color: #374151;
  line-height: 1.6;
  margin-bottom: 16rpx;
}

.review-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-date-text {
  font-size: 22rpx;
  color: #9CA3AF;
}

.review-like-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
  background: #F3F5F4;
}

.like-thumb {
  position: relative;
  width: 18rpx;
  height: 18rpx;
  transform: rotate(45deg);
  background: #6B7280;
  flex-shrink: 0;
}

.like-thumb::before,
.like-thumb::after {
  content: '';
  position: absolute;
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: #6B7280;
}

.like-thumb::before { left: -9rpx; }
.like-thumb::after { top: -9rpx; }

.like-num {
  font-size: 22rpx;
  color: #6B7280;
}

.review-empty {
  font-size: 26rpx;
  color: #9CA3AF;
}

.review-more {
  font-size: 26rpx;
  color: #15803D;
  flex-shrink: 0;
}

.bottom-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #E3E7E5;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
}

.bottom-right {
  flex: 1;
  display: flex;
  gap: 16rpx;
}

.btn-cart, .btn-buy {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 44rpx;
  font-size: 28rpx;
  color: #FFFFFF;
  font-weight: 600;
  min-width: 88rpx;
}

.btn-cart {
  background: #A16207;
}

.btn-buy {
  background: #15803D;
}
</style>
