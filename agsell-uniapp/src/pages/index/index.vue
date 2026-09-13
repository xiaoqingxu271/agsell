<template>
  <view class="index-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input" @click="onSearchTap" role="button" aria-label="搜索商品">
        <image class="search-icon" src="/static/icon-search.png" mode="aspectFit" alt="搜索" />
        <text class="search-placeholder">搜索商品</text>
      </view>
    </view>

    <!-- 轮播图 -->
    <swiper v-if="banners.length > 0" class="banner-swiper" indicator-dots autoplay circular interval="5000">
      <swiper-item v-for="banner in banners" :key="banner.id">
        <image
          class="banner-image"
          :src="banner.image"
          mode="aspectFill"
          lazy-load
          @click="onBannerTap(banner)"
          :show-menu-by-longpress="false"
          :alt="banner.title || '轮播图'"
        />
      </swiper-item>
    </swiper>
    <view v-else class="banner-placeholder">
      <text class="placeholder-text">新鲜水果 · 有机蔬菜 · 当季特产</text>
    </view>

    <!-- 限时秒杀横卡（无进行中/未开始活动时整条隐藏） -->
    <view v-if="seckillActivities.length > 0" class="seckill-card">
      <view class="seckill-head">
        <view class="seckill-title-wrap">
          <text class="seckill-title">限时秒杀</text>
          <text class="seckill-sub">超值抢购 · 手慢无</text>
        </view>
        <view class="seckill-more" @click="onSeckillMoreTap" role="button" aria-label="查看全部秒杀">
          <text class="seckill-more-text">更多</text>
          <text class="seckill-more-arrow">›</text>
        </view>
      </view>
      <scroll-view scroll-x class="seckill-scroll">
        <view class="seckill-scroll-inner">
          <view
            v-for="act in seckillActivities"
            :key="act.id"
            class="seckill-item"
            @click="onSeckillItemTap(act)"
            role="button"
            :aria-label="act.productName"
          >
            <image class="seckill-img" :src="act.productImage" mode="aspectFill" lazy-load :alt="act.productName" />
            <view class="seckill-price-row">
              <text class="seckill-price">¥{{ act.seckillPrice }}</text>
              <text class="seckill-origin">¥{{ act.productPrice }}</text>
            </view>
            <view class="seckill-btn" :class="{ 'seckill-btn-disabled': act.activityStatus !== 2 }">
              {{ act.activityStatus === 1 ? '即将开始' : '立即抢购' }}
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 分类快捷入口 -->
    <view class="category-grid">
      <view
        v-for="cat in categories"
        :key="cat.id"
        class="category-item"
        @click="onCategoryTap(cat)"
        role="button"
        :aria-label="cat.name"
      >
        <view class="category-icon-wrap">
          <image v-if="cat.icon" class="category-icon" :src="cat.icon" mode="aspectFill" lazy-load :alt="cat.name" />
          <text v-else class="category-icon-default">{{ cat.name[0] }}</text>
        </view>
        <text class="category-name">{{ cat.name }}</text>
      </view>
    </view>

    <!-- 热销推荐 -->
    <view class="section">
      <view class="section-title">
        <image class="section-icon" src="/static/icon-hot.png" mode="aspectFit" alt="热销" />
        <text>热销推荐</text>
      </view>
      <scroll-view scroll-x class="product-scroll">
        <view class="product-scroll-inner">
          <ProductCard
            v-for="item in hotProducts"
            :key="item.id"
            :product="item"
            @tap="onProductTap"
          />
        </view>
      </scroll-view>
    </view>

    <!-- 新品推荐 -->
    <view class="section">
      <view class="section-title">
        <image class="section-icon" src="/static/icon-new.png" mode="aspectFit" alt="新品" />
        <text>新品推荐</text>
      </view>
      <scroll-view scroll-x class="product-scroll">
        <view class="product-scroll-inner">
          <ProductCard
            v-for="item in newProducts"
            :key="item.id"
            :product="item"
            @tap="onProductTap"
          />
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ProductCard from '../../components/ProductCard/ProductCard.vue'
import { getBannerList } from '../../api/banner'
import { getCategoryList, getHotProducts, getNewProducts } from '../../api/product'
import { getSeckillList } from '../../api/seckill'

const banners = ref([])
const categories = ref([])
const hotProducts = ref([])
const newProducts = ref([])
const seckillActivities = ref([])

onMounted(async () => {
  await loadBanners()
  await loadSeckill()
  await loadCategories()
  await loadHotProducts()
  await loadNewProducts()
})

async function loadSeckill() {
  const res = await getSeckillList({ filter: 0 })
  if (res.code === 0) seckillActivities.value = res.data || []
}

async function loadBanners() {
  const res = await getBannerList()
  if (res.code === 0) banners.value = res.data
}

async function loadCategories() {
  const res = await getCategoryList()
  if (res.code === 0) {
    categories.value = res.data.filter(c => !c.parentId || String(c.parentId) === '0')
  }
}

async function loadHotProducts() {
  const res = await getHotProducts(10)
  if (res.code === 0) hotProducts.value = res.data
}

async function loadNewProducts() {
  const res = await getNewProducts(10)
  if (res.code === 0) newProducts.value = res.data
}

function onCategoryTap(cat) {
  uni.switchTab({ url: '/pages/category/category' })
}

function onProductTap(product) {
  const id = String(product?.id || '')
  if (!id || id === 'undefined' || id === 'null') {
    uni.showToast({ title: '商品ID无效', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/product?id=${id}` })
}

function onBannerTap(banner) {
  if (banner.link) {
    if (banner.link.startsWith('/product/')) {
      const id = banner.link.replace('/product/', '')
      uni.navigateTo({ url: `/pages/product/product?id=${id}` })
    } else if (banner.link.startsWith('/product/list')) {
      uni.switchTab({ url: '/pages/category/category' })
    }
  }
}

function onSearchTap() {
  uni.showToast({ title: '搜索功能开发中', icon: 'none' })
}

function onSeckillMoreTap() {
  uni.navigateTo({ url: '/pages/seckill/list' })
}

function onSeckillItemTap(act) {
  const code = String(act?.activityCode || '')
  if (!code || code === 'undefined' || code === 'null') {
    uni.showToast({ title: '活动编号无效', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/seckill/detail?code=${code}` })
}
</script>

<style scoped>
.index-page {
  background: #F4F6F5;
  min-height: 100vh;
  padding-bottom: 24rpx;
}

.search-bar {
  background: #F0FDF4;
  padding: 20rpx 24rpx 32rpx;
}

.search-input {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  border-radius: 44rpx;
  height: 76rpx;
  padding: 0 24rpx;
  min-width: 88rpx;
  box-shadow: 0 4rpx 16rpx rgba(21, 128, 61, 0.10);
}

.search-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
}

.search-placeholder {
  font-size: 26rpx;
  color: #9CA3AF;
}

.banner-swiper {
  height: 360rpx;
  width: calc(100% - 48rpx);
  margin: -16rpx 24rpx 0;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 24rpx rgba(16, 24, 40, 0.10);
}

.banner-image {
  width: 100%;
  height: 360rpx;
  background: #F4F6F5;
  border-radius: 24rpx;
}

.banner-placeholder {
  height: 360rpx;
  width: calc(100% - 48rpx);
  margin: -16rpx 24rpx 0;
  background: linear-gradient(135deg, #F0FDF4 0%, #DCFCE7 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
  border: 1rpx solid #DCFCE7;
  box-shadow: 0 8rpx 24rpx rgba(16, 24, 40, 0.06);
}

.placeholder-text {
  font-size: 32rpx;
  color: #166534;
  font-weight: 600;
  letter-spacing: 4rpx;
}

.category-grid {
  display: flex;
  flex-wrap: wrap;
  background: #FFFFFF;
  margin: 24rpx;
  padding: 24rpx 0;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

/* 限时秒杀横卡（红橙系） */
.seckill-card {
  margin: 16rpx 24rpx 24rpx;
  padding: 20rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #FF4D2E 0%, #FF7A1A 100%);
  box-shadow: 0 8rpx 24rpx rgba(255, 77, 46, 0.28);
}

.seckill-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14rpx;
}

.seckill-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.seckill-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #FFFFFF;
}

.seckill-sub {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.9);
}

.seckill-more {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.seckill-more-text {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.95);
}

.seckill-more-arrow {
  font-size: 30rpx;
  color: #FFFFFF;
  line-height: 1;
}

.seckill-scroll {
  white-space: nowrap;
}

.seckill-scroll-inner {
  display: inline-flex;
  gap: 16rpx;
}

.seckill-item {
  width: 168rpx;
  flex-shrink: 0;
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 10rpx;
  box-sizing: border-box;
}

.seckill-img {
  width: 148rpx;
  height: 148rpx;
  border-radius: 12rpx;
  background: #F4F6F5;
}

.seckill-price-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-top: 8rpx;
}

.seckill-price {
  font-size: 28rpx;
  font-weight: 700;
  color: #DC2626;
}

.seckill-origin {
  font-size: 20rpx;
  color: #9CA3AF;
  text-decoration: line-through;
}

.seckill-btn {
  margin-top: 8rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  background: #FF4D2E;
  color: #FFFFFF;
  font-size: 22rpx;
  font-weight: 600;
  border-radius: 24rpx;
}

.seckill-btn-disabled {
  background: #D1D5DB;
}

.category-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 0;
  min-height: 88rpx;
}

.category-icon-wrap {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.category-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #F0FDF4;
}

.category-icon-default {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #F0FDF4;
  color: #15803D;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}

.category-name {
  font-size: 24rpx;
  color: #1F2937;
  margin-top: 8rpx;
}

.section {
  background: #FFFFFF;
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #15803D;
  margin-bottom: 20rpx;
}

.section-icon {
  width: 32rpx;
  height: 32rpx;
}

.product-scroll {
  white-space: nowrap;
}

.product-scroll-inner {
  display: inline-flex;
  gap: 16rpx;
}

.product-scroll-inner :deep(.product-card) {
  width: 200rpx;
  flex-shrink: 0;
}

.product-scroll-inner :deep(.product-info) {
  padding: 12rpx;
}

.product-scroll-inner :deep(.product-name) {
  font-size: 24rpx;
  line-height: 1.35;
}

.product-scroll-inner :deep(.price) {
  font-size: 30rpx;
}

.product-scroll-inner :deep(.original-price) {
  font-size: 20rpx;
}

.product-scroll-inner :deep(.sales) {
  font-size: 20rpx;
}
</style>
