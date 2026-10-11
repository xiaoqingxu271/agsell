<template>
  <view class="product-page">
    <NavBar title="商品详情" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="product-scroll">
      <!-- 加载骨架屏 -->
      <view v-if="loading" class="page-skeleton">
        <wd-skeleton class="sk-banner" type="rect" width="100%" height="750rpx" border-radius="0" />
        <view class="band sk-card">
          <wd-skeleton type="rect" width="220rpx" height="56rpx" />
          <wd-skeleton type="rect" width="90%" height="40rpx" margin-top="24rpx" />
          <wd-skeleton type="rect" width="55%" height="28rpx" margin-top="16rpx" />
        </view>
        <view class="band sk-card">
          <wd-skeleton type="rect" width="40%" height="32rpx" />
          <wd-skeleton type="rect" width="100%" height="88rpx" margin-top="24rpx" border-radius="16rpx" />
        </view>
      </view>
      <block v-else>
      <!-- 商品图片轮播（1:1 主图 + 空态占位 + 页码） -->
      <view class="hero">
        <swiper
          class="product-swiper"
          indicator-dots
          :autoplay="allImages.length > 1"
          circular
          interval="3000"
          indicator-color="rgba(0,0,0,0.18)"
          indicator-active-color="#E1251B"
          @change="onSwiperChange"
        >
          <swiper-item v-for="(img, i) in allImages" :key="i">
            <image :src="img" mode="aspectFill" lazy-load class="swiper-image" :alt="product.name || '商品图片'" @click="onPreviewImage(img)" />
          </swiper-item>
          <swiper-item v-if="allImages.length === 0">
            <view class="hero-ph">
              <wd-icon name="picture" size="260rpx" custom-class="hero-ph-ic" />
            </view>
          </swiper-item>
        </swiper>
        <text class="hero-pg">{{ swiperIdx + 1 }}/{{ slideCount }}</text>
      </view>

      <!-- 价格 + 标题带（整宽白底，无外边距） -->
      <view class="band band-price">
        <view class="price-row">
          <view class="price-wrap">
            <text class="price-symbol">¥</text>
            <text class="price-int">{{ product.price }}</text>
            <text v-if="product.originalPrice" class="original-price">¥{{ product.originalPrice }}</text>
          </view>
          <text class="sold-text">已售{{ product.sales || 0 }}</text>
        </view>
        <view class="name"><text v-if="titlePre" class="name-pre">{{ titlePre }}</text>{{ product.name }}</view>
        <view v-if="titleSub" class="subtitle">{{ titleSub }}</view>
        <view class="meta-row">
          <text v-if="product.categoryName" class="meta-tag tr">{{ product.categoryName }}</text>
          <text v-if="product.parentCategoryName" class="meta-tag tk">{{ product.parentCategoryName }}</text>
          <text v-if="product.hasTrace" class="meta-tag tg">产地溯源</text>
          <text class="meta-tag tk">库存{{ product.stock || 0 }}</text>
        </view>
      </view>

      <!-- 产地信息 + 溯源入口 -->
      <view class="band band-rows">
        <view v-if="product.origin" class="drow">
          <text class="dlabel">产地</text>
          <text class="dvalue">{{ product.origin }}</text>
        </view>
        <view v-if="product.harvestDate" class="drow">
          <text class="dlabel">采摘</text>
          <text class="dvalue">{{ product.harvestDate }}</text>
        </view>
        <view v-if="product.shelfLife" class="drow">
          <text class="dlabel">保质期</text>
          <text class="dvalue">{{ product.shelfLife }}</text>
        </view>
        <view v-if="product.storage" class="drow">
          <text class="dlabel">储存</text>
          <text class="dvalue">{{ product.storage }}</text>
        </view>
        <!-- 溯源入口 -->
        <view v-if="product.hasTrace" class="trace-entry" @click="onViewTrace" role="button">
          <view class="trace-entry-icon" aria-hidden="true"></view>
          <view class="trace-entry-col">
            <text class="trace-entry-title">产地溯源档案</text>
            <text class="trace-entry-batch">{{ product.traceBatchNo }}</text>
          </view>
          <wd-icon name="chevron-right" size="28rpx" custom-class="trace-entry-arrow" />
        </view>
      </view>

      <!-- 规格选择 -->
      <view v-if="specs && specs.length > 0" class="band band-spec">
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
      <view class="band band-qty">
        <view class="qty-left">
          <text class="section-title section-title-inline">购买数量</text>
          <text class="qty-stock">库存{{ stock }}</text>
        </view>
        <wd-input-number v-model="quantity" :min="1" :max="maxStock" :disabled="loading" input-width="72rpx" />
      </view>

      <!-- 商品详情 -->
      <view v-if="product.description" class="band band-detail">
        <view class="section-title">商品详情</view>
        <view class="detail-content" v-html="product.description"></view>
      </view>

      <!-- 商品评价：标题行（数量 + 好评率）+ 描边关键词标签 + 评价列表 -->
      <view class="band band-review">
        <view class="review-head" @click="onViewReviews" role="button" aria-label="查看全部评价">
          <text class="review-head-t">商品评价（{{ reviewTotal }}）</text>
          <view class="review-head-r">
            <text v-if="positiveRate > 0" class="review-rate">好评 {{ positiveRate }}%</text>
            <wd-icon name="chevron-right" size="24rpx" custom-class="review-arrow" />
          </view>
        </view>
        <view v-if="keywords.length > 0" class="review-tags">
          <text v-for="kw in keywords" :key="kw" class="kw-tag">{{ kw }}</text>
        </view>
        <view v-if="reviews.length > 0" class="review-list">
          <view v-for="r in reviews.slice(0, 2)" :key="r.id" class="review-card-item">
            <view class="review-user-row">
              <image v-if="r.userAvatar" class="review-avatar" :src="r.userAvatar" mode="aspectFill" lazy-load />
              <view v-else class="review-avatar review-avatar--ph">
                <wd-icon name="user" size="28rpx" custom-class="review-avatar-ic" />
              </view>
              <view class="review-user-col">
                <text class="review-username">{{ r.userName || '匿名用户' }}</text>
                <StarRating :rating="r.rating || 5" :readonly="true" size="24rpx" />
              </view>
            </view>
            <text class="review-content-text">{{ r.content }}</text>
            <view class="review-card-footer">
              <text class="review-date-text">{{ formatDate(r.createTime) }}</text>
              <view class="review-like-btn">
                <wd-icon name="thumb-up" size="24rpx" custom-class="like-icon" />
                <text class="like-num">{{ r.likeCount || 0 }}</text>
              </view>
            </view>
          </view>
        </view>
        <text v-else class="review-empty">暂无评价</text>
      </view>
      </block>
    </scroll-view>

    <!-- 底部操作栏：功能入口 + 加购（橙）+ 立即购买（红） -->
    <view class="cbar">
      <view class="fn" @click="onGoService" role="button" aria-label="在线客服">
        <wd-icon name="service" size="34rpx" custom-class="fn-icon" />
        <text class="fn-label">客服</text>
      </view>
      <view class="fn" @click="onGoCart" role="button" aria-label="购物车">
        <wd-icon name="cart" size="34rpx" custom-class="fn-icon" />
        <text class="fn-label">购物车</text>
      </view>
      <view class="act-btn act-cart" :class="{ 'act-disabled': loading }" @click="onAddToCart" role="button">加入购物车</view>
      <view class="act-btn act-buy" :class="{ 'act-disabled': loading }" @click="onBuyNow" role="button">立即购买</view>
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
// 口碑摘要（好评率 + 好评关键词，来自评价情感分析聚合）
const reviewSummary = ref(null)
const relatedProducts = ref([])
const loading = ref(true)
const swiperIdx = ref(0)

const allImages = computed(() => {
  if (images.value && images.value.length > 0) {
    return images.value
  }
  if (product.value.mainImage) {
    return [product.value.mainImage]
  }
  return []
})

// 轮播至少保留 1 页，空态占位也要显示页码
const slideCount = computed(() => allImages.value.length || 1)

// 参考稿标题带：卖点首词作红色前缀，其余卖点降为灰色副标题
const titlePre = computed(() => String(product.value.subtitle || '').trim().split(/\s+/)[0] || '')
const titleSub = computed(() => String(product.value.subtitle || '').trim().split(/\s+/).slice(1).join(' · '))

const reviewTotal = computed(() => Number(reviewSummary.value?.total) || reviews.value.length || 0)
const positiveRate = computed(() => Number(reviewSummary.value?.positiveRate) || 0)
const keywords = computed(() => (reviewSummary.value?.topKeywords || []).slice(0, 6))

function onSwiperChange(e) {
  swiperIdx.value = e.detail.current
}

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
  loading.value = true
  try {
    const res = await getProductDetail(id)
    if (res.code === 0 && res.data) {
      product.value = res.data
      reviewSummary.value = res.data.reviewSummary || null
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
  } finally {
    loading.value = false
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

const maxStock = computed(() => selectedSpec.value?.stock || product.value.stock || 999)

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

function onGoCart() {
  uni.switchTab({ url: '/pages/cart/cart' })
}

function onGoService() {
  uni.navigateTo({ url: '/pages/ai-chat/chat' })
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

<style lang="scss" scoped>
.product-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $uni-bg-color-grey;
}

.product-scroll {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 200rpx;
}

/* 加载骨架屏 */
.page-skeleton .sk-banner {
  display: block;
}

.sk-card {
  padding: $uni-spacing-col-lg;
}

/* 主图区：1:1 画幅 + 空态占位 + 页码胶囊 */
.hero {
  position: relative;
  width: 100%;
  height: 750rpx;
}

.product-swiper {
  width: 100%;
  height: 750rpx;
}

.swiper-image {
  width: 100%;
  height: 750rpx;
}

.hero-ph {
  width: 100%;
  height: 750rpx;
  background: $uni-bg-color-img;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.hero-ph-ic) {
  color: $uni-color-img-icon;
}

.hero-pg {
  position: absolute;
  right: 24rpx;
  bottom: 24rpx;
  font-size: 20rpx;
  line-height: 28rpx;
  color: $uni-text-color-inverse;
  background: rgba(0, 0, 0, 0.35);
  padding: 4rpx 16rpx;
  border-radius: 18rpx;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
}

/* 整宽白底信息带，带间以页面灰底分隔 */
.band {
  background: $uni-bg-color;
}

.band + .band {
  border-top: 16rpx solid $uni-bg-color-grey;
}

.band-price {
  padding: 20rpx 24rpx 16rpx;
}

.price-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.price-wrap {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
}

.price-symbol {
  font-size: 26rpx;
  font-weight: 700;
  color: $uni-color-price;
}

.price-int {
  font-size: 52rpx;
  font-weight: 700;
  color: $uni-color-price;
  line-height: 1;
  letter-spacing: -1rpx;
}

.original-price {
  font-size: 22rpx;
  color: $uni-text-color-3;
  text-decoration: line-through;
  margin-left: 6rpx;
}

.sold-text {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
  padding-bottom: 8rpx;
}

.name {
  font-size: 30rpx;
  font-weight: 600;
  color: $uni-text-color;
  margin-top: 16rpx;
  line-height: 44rpx;
}

.name-pre {
  color: $uni-color-primary;
  margin-right: 8rpx;
}

.subtitle {
  font-size: 22rpx;
  color: $uni-text-color-grey;
  margin-top: 8rpx;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8rpx;
  margin-top: 14rpx;
}

/* 三类语义描边标签：tg 服务绿 / tr 促销红 / tk 属性灰 */
.meta-tag {
  font-size: 18rpx;
  line-height: 28rpx;
  padding: 0 8rpx;
  border-radius: 4rpx;
  border: 2rpx solid transparent;
}

.meta-tag.tg {
  color: $uni-color-service;
  border-color: $uni-color-service-100;
}

.meta-tag.tr {
  color: $uni-color-primary;
  border-color: $uni-color-primary-100;
}

.meta-tag.tk {
  color: $uni-text-color-grey;
  border-color: $uni-border-color;
}

/* ===== 产地/溯源信息行 ===== */
.band-rows {
  padding: 0 24rpx;
}

.drow {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 20rpx 0;
  border-bottom: 2rpx solid $uni-border-color-divider;
  font-size: 24rpx;
}

.dlabel {
  color: $uni-text-color-placeholder;
  width: 84rpx;
  flex-shrink: 0;
  line-height: 36rpx;
  white-space: nowrap;
}

.dvalue {
  flex: 1;
  color: $uni-text-color;
  line-height: 36rpx;
}

.trace-entry {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 20rpx;
}

.trace-entry:active {
  background: $uni-color-service-50;
}

.trace-entry-icon {
  width: 44rpx;
  height: 44rpx;
  border-radius: 12rpx;
  background: $uni-color-service;
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
  border: 3rpx solid $uni-bg-color;
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
  border: 3rpx solid $uni-bg-color;
  border-left: none;
  border-top: none;
  border-bottom-right-radius: 4rpx;
}

.trace-entry-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.trace-entry-title {
  font-size: 26rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.trace-entry-batch {
  font-size: 22rpx;
  color: $uni-color-service;
  font-family: $uni-font-family-num;
  margin-top: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.trace-entry-arrow) {
  color: $uni-text-color-disable;
  flex-shrink: 0;
}

/* ===== 规格 ===== */
.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $uni-text-color;
  margin-bottom: 20rpx;
}

.section-title-inline {
  margin-bottom: 0;
}

.band-spec,
.band-qty,
.band-detail {
  padding: 24rpx;
}

.band-review {
  padding: 0 0 12rpx;
}

.spec-list {
  display: flex;
  flex-wrap: wrap;
  gap: $uni-spacing-row-base;
}

.spec-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 18rpx $uni-spacing-col-lg;
  border: 1rpx solid $uni-border-color;
  border-radius: $uni-border-radius-base;
  min-width: 180rpx;
  min-height: 88rpx;
  box-sizing: border-box;
  background: $uni-bg-color;
  transition: border-color 150ms ease-out, background 150ms ease-out;
}

.spec-item.active {
  border-color: $uni-color-primary;
  background: $uni-color-primary-50;
}

.spec-name {
  font-size: 26rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.spec-item.active .spec-name {
  color: $uni-color-primary;
}

.spec-price {
  font-size: 24rpx;
  color: $uni-color-price;
  margin-top: $uni-spacing-row-sm;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
}

.spec-stock {
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
  margin-top: 4rpx;
}

/* ===== 数量 ===== */
.band-qty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $uni-spacing-row-lg;
}

.qty-left {
  display: flex;
  align-items: baseline;
  gap: 20rpx;
}

.qty-stock {
  font-size: $uni-font-size-sm;
  color: $uni-text-color-placeholder;
}

.detail-content {
  font-size: $uni-font-size-base;
  color: $uni-text-color;
  line-height: 1.8;
}

/* ===== 评价（设计稿 .grp：标题行与标签行自带 24rpx 左右内边距） ===== */
.review-head {
  display: flex;
  align-items: center;
  padding: 24rpx 24rpx 8rpx;
}

.review-head-t {
  font-size: 28rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.review-head-r {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2rpx;
}

.review-rate {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
}

:deep(.review-arrow) {
  color: $uni-text-color-disable;
}

.review-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  padding: 0 24rpx;
}

.kw-tag {
  font-size: 18rpx;
  line-height: 28rpx;
  padding: 0 8rpx;
  border-radius: 4rpx;
  border: 2rpx solid $uni-color-service-100;
  color: $uni-color-service;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  padding: 20rpx 24rpx 0;
}

.review-card-item {
  padding-bottom: $uni-spacing-col-lg;
  border-bottom: 2rpx solid $uni-border-color-divider;
}

.review-card-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.review-user-row {
  display: flex;
  align-items: center;
  margin-bottom: $uni-spacing-row-base;
}

.review-avatar {
  width: 52rpx;
  height: 52rpx;
  border-radius: $uni-border-radius-circle;
  background: $uni-bg-color-img;
  flex-shrink: 0;
}

.review-avatar--ph {
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.review-avatar-ic) {
  color: $uni-color-img-icon;
}

.review-user-col {
  margin-left: $uni-spacing-row-base;
  flex: 1;
}

.review-username {
  display: block;
  font-size: 24rpx;
  color: $uni-text-color;
  font-weight: 500;
  margin-bottom: 6rpx;
}

.review-content-text {
  display: block;
  font-size: 24rpx;
  color: $uni-text-color;
  line-height: 36rpx;
  margin-bottom: $uni-spacing-row-base;
}

.review-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-date-text {
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
}

.review-like-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

:deep(.like-icon) {
  color: $uni-text-color-placeholder;
}

.like-num {
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
  font-family: $uni-font-family-num;
}

.review-empty {
  display: block;
  padding: 20rpx 24rpx 24rpx;
  font-size: 24rpx;
  color: $uni-text-color-placeholder;
}

/* ===== 底部操作栏 ===== */
.cbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  height: 116rpx;
  padding-left: 8rpx;
  padding-right: 16rpx;
  padding-bottom: env(safe-area-inset-bottom);
  background: $uni-bg-color;
  border-top: 2rpx solid $uni-border-color-divider;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
}

.fn {
  width: 96rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}

:deep(.fn-icon) {
  color: $uni-text-color-grey;
}

.fn-label {
  font-size: 18rpx;
  color: $uni-text-color-grey;
  line-height: 1;
}

.act-btn {
  flex: 1;
  height: 84rpx;
  margin-left: 12rpx;
  border-radius: 42rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 600;
  color: $uni-text-color-inverse;
}

.act-btn:active {
  opacity: 0.88;
}

.act-cart {
  background: $uni-color-cart;
}

.act-buy {
  background: $uni-color-primary;
}

.act-disabled {
  opacity: $uni-opacity-disabled;
}
</style>
