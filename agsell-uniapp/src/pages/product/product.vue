<template>
  <view class="product-page">
    <NavBar title="商品详情" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="product-scroll">
      <!-- 商品图片轮播 -->
      <swiper class="product-swiper" indicator-dots autoplay circular interval="3000">
        <swiper-item v-for="(img, i) in images" :key="i">
          <image :src="img" mode="aspectFill" class="swiper-image" :alt="product.name || '商品图片'" />
        </swiper-item>
        <swiper-item v-if="images.length === 0">
          <image :src="product.mainImage || '/static/default-product.png'" mode="aspectFill" class="swiper-image" :alt="product.name || '商品图片'" />
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
            :aria-label="`规格 ${spec.specName}`"
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
      <view class="review-preview card" @click="onViewReviews">
        <view class="section-title">用户评价</view>
        <view v-if="reviews.length > 0" class="review-items">
          <view v-for="r in reviews.slice(0, 2)" :key="r.id" class="review-item">
            <view class="review-header">
              <StarRating :rating="r.rating || 5" :readonly="true" size="28rpx" />
            </view>
            <text class="review-text">{{ r.content }}</text>
            <text class="review-time">{{ r.createTime }}</text>
          </view>
        </view>
        <text v-else class="review-empty">暂无评价</text>
        <text class="review-more">查看全部评价</text>
      </view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="bottom-left">
        <view class="bottom-action" @click="onAddToCart" role="button" aria-label="购物车">
          <svg class="action-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="9" cy="21" r="1"></circle>
            <circle cx="20" cy="21" r="1"></circle>
            <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
          </svg>
          <text class="action-text">购物车</text>
        </view>
      </view>
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

onLoad((options) => {
  const { id } = options
  if (id) {
    loadProductDetail(id)
    loadReviews(id)
    loadRelatedProducts(id)
  }
})

async function loadProductDetail(id) {
  if (!id) return
  const res = await getProductDetail(id)
  if (res.code === 0) {
    product.value = res.data
    if (res.data.images) {
      images.value = Array.isArray(res.data.images) ? res.data.images : []
    }
    if (res.data.specs && res.data.specs.length > 0) {
      specs.value = res.data.specs
      selectedSpec.value = res.data.specs[0]
    }
    quantity.value = 1
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

const stock = computed(() => selectedSpec.value?.stock ?? product.value.stock ?? 0)
</script>

<style scoped>
.product-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F0FDF4;
}

.product-scroll {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 160rpx;
}

.swiper-image {
  width: 100%;
  height: 750rpx;
  border-radius: 12rpx;
}

.card {
  background: #FFFFFF;
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1px solid #BBF7D0;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
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

.price::before { content: '¥'; font-size: 28rpx; }

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
  border-radius: 8rpx;
  line-height: 1.4;
}

.tag-primary {
  background: #BBF7D0;
  color: #14532D;
}

.tag-info {
  background: #F3F4F6;
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

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #14532D;
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
  border: 1px solid #BBF7D0;
  border-radius: 12rpx;
  min-width: 180rpx;
  min-height: 88rpx;
  box-sizing: border-box;
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
  border: 1px solid #D1D5DB;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #1F2937;
  background: #FFFFFF;
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

.review-preview .review-items {
  margin-bottom: 16rpx;
}

.review-item {
  padding: 16rpx 0;
  border-bottom: 1px solid #E5E7EB;
}

.review-header {
  margin-bottom: 8rpx;
}

.review-text {
  font-size: 26rpx;
  color: #1F2937;
  line-height: 1.5;
}

.review-time {
  font-size: 22rpx;
  color: #6B7280;
  margin-left: 16rpx;
}

.review-empty {
  font-size: 26rpx;
  color: #9CA3AF;
}

.review-more {
  font-size: 26rpx;
  color: #15803D;
  float: right;
}

.bottom-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1px solid #E5E7EB;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
}

.bottom-left {
  display: flex;
  gap: 24rpx;
}

.bottom-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  color: #6B7280;
  padding: 8rpx 16rpx;
  min-width: 88rpx;
  min-height: 88rpx;
}

.action-icon {
  width: 40rpx;
  height: 40rpx;
  color: #6B7280;
  margin-bottom: 4rpx;
}

.bottom-right {
  flex: 1;
  display: flex;
  gap: 16rpx;
  justify-content: flex-end;
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
