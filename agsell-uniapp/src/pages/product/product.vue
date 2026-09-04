<template>
  <view class="product-page">
    <NavBar title="商品详情" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="product-scroll">
      <!-- 商品图片轮播 -->
      <swiper class="product-swiper" indicator-dots autoplay circular interval="3000">
        <swiper-item v-for="(img, i) in images" :key="i">
          <image :src="img" mode="aspectFill" class="swiper-image" />
        </swiper-item>
        <swiper-item v-if="images.length === 0">
          <image :src="product.mainImage || '/static/default-product.png'" mode="aspectFill" class="swiper-image" />
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
          <text v-if="product.categoryName" class="meta-tag">{{ product.categoryName }}</text>
          <text v-if="product.parentCategoryName" class="meta-tag">{{ product.parentCategoryName }}</text>
          <text class="meta-text">销量 {{ product.sales }}</text>
          <text class="meta-text">库存 {{ product.stock }}</text>
        </view>
        <!-- 产地信息 -->
        <view v-if="product.origin || product.harvestDate" class="origin-info">
          <text v-if="product.origin" class="origin-item">📍 {{ product.origin }}</text>
          <text v-if="product.harvestDate" class="origin-item">🌾 采摘: {{ product.harvestDate }}</text>
          <text v-if="product.shelfLife" class="origin-item">📦 保质期: {{ product.shelfLife }}</text>
          <text v-if="product.storage" class="origin-item">🧊 储存: {{ product.storage }}</text>
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
          <view class="qty-btn" @click="changeQty(-1)">-</view>
          <text class="qty-value">{{ quantity }}</text>
          <view class="qty-btn" @click="changeQty(1)">+</view>
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
            <text class="review-text">{{ r.content }}</text>
            <text class="review-time">{{ r.createTime }}</text>
          </view>
        </view>
        <text v-else class="review-empty">暂无评价</text>
        <text class="review-more">查看全部评价 ›</text>
      </view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="bottom-left">
        <view class="bottom-action" @click="onAddToCart">
          <text class="action-icon">🛒</text>
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
    // 解析 images 字段
    if (res.data.images) {
      images.value = Array.isArray(res.data.images) ? res.data.images : []
    }
    // 规格列表
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
  background: #f5f5f5;
}

.product-scroll {
  flex: 1;
  overflow-y: auto;
}

.swiper-image {
  width: 100%;
  height: 750rpx;
}

.card {
  background: #fff;
  margin: 20rpx;
  padding: 24rpx;
  border-radius: 16rpx;
}

.info-section .price-row {
  display: flex;
  align-items: baseline;
}

.price {
  font-size: 48rpx;
  color: #FF9800;
  font-weight: bold;
}

.price::before { content: '¥'; font-size: 28rpx; }

.original-price {
  font-size: 28rpx;
  color: #ccc;
  text-decoration: line-through;
  margin-left: 16rpx;
}

.name {
  font-size: 36rpx;
  font-weight: bold;
  color: #333;
  margin-top: 16rpx;
  line-height: 1.4;
}

.subtitle {
  font-size: 26rpx;
  color: #666;
  margin-top: 8rpx;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.meta-tag {
  font-size: 22rpx;
  color: #fff;
  background: #4CAF50;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
}

.meta-text {
  font-size: 24rpx;
  color: #999;
  margin-left: 24rpx;
}

.origin-info {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}

.origin-item {
  font-size: 24rpx;
  color: #666;
}

.section-title {
  font-size: 30rpx;
  font-weight: bold;
  color: #333;
  margin-bottom: 20rpx;
}

.spec-list {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}

.spec-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx 32rpx;
  border: 2rpx solid #eee;
  border-radius: 12rpx;
  min-width: 180rpx;
}

.spec-item.active {
  border-color: #4CAF50;
  background: #f0fff0;
}

.spec-name {
  font-size: 28rpx;
  font-weight: bold;
  color: #333;
}

.spec-price {
  font-size: 26rpx;
  color: #FF9800;
  margin-top: 8rpx;
}

.spec-stock {
  font-size: 22rpx;
  color: #999;
  margin-top: 4rpx;
}

.quantity-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.qty-btn {
  width: 56rpx;
  height: 56rpx;
  border: 1rpx solid #ccc;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #666;
}

.qty-value {
  font-size: 32rpx;
  min-width: 60rpx;
  text-align: center;
}

.qty-stock {
  font-size: 24rpx;
  color: #999;
  margin-left: auto;
}

.detail-content {
  font-size: 28rpx;
  color: #333;
  line-height: 1.8;
}

.review-preview .review-items {
  margin-bottom: 16rpx;
}

.review-item {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.review-text {
  font-size: 26rpx;
  color: #333;
}

.review-time {
  font-size: 22rpx;
  color: #999;
  margin-left: 16rpx;
}

.review-empty {
  font-size: 26rpx;
  color: #999;
}

.review-more {
  font-size: 26rpx;
  color: #4CAF50;
  float: right;
}

.bottom-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
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
  font-size: 20rpx;
  color: #666;
  padding: 8rpx 16rpx;
}

.action-icon {
  font-size: 40rpx;
}

.bottom-right {
  flex: 1;
  display: flex;
  gap: 16rpx;
  justify-content: flex-end;
}

.btn-cart, .btn-buy {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 40rpx;
  font-size: 28rpx;
  color: #fff;
}

.btn-cart {
  background: #FF9800;
}

.btn-buy {
  background: #4CAF50;
}
</style>
