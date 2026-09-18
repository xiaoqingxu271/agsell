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
import { request } from '../../utils/request'

const banners = ref([])
const categories = ref([])
const hotProducts = ref([])
const newProducts = ref([])
const seckillActivities = ref([])

onMounted(async () => {
  setNavTitle()
  await loadBanners()
  await loadSeckill()
  await loadCategories()
  await loadHotProducts()
  await loadNewProducts()
})

/** 首页导航栏展示系统配置的平台名称（管理端可改），失败时保留 pages.json 默认标题 */
async function setNavTitle() {
  try {
    const cached = uni.getStorageSync('platform_name')
    if (cached) uni.setNavigationBarTitle({ title: cached })
    const res = await request('GET', '/system/config', null, { params: { keys: 'platform_name' } })
    if (res.code === 0 && res.data?.platform_name) {
      uni.setStorageSync('platform_name', res.data.platform_name)
      uni.setNavigationBarTitle({ title: res.data.platform_name })
    }
  } catch (e) {
    console.warn('[Index] 加载平台名称失败，使用默认标题', e)
  }
}

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
  uni.navigateTo({ url: '/pages/search/search' })
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
  background: #F5F5F5;
  min-height: 100vh;
  padding-bottom: 24rpx;
}

.search-bar {
  background: #00B578;
  padding: 20rpx 24rpx 32rpx;
}

.search-input {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  border-radius: 999rpx;
  height: 72rpx;
  padding: 0 24rpx;
  min-width: 88rpx;
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
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.08);
}

.banner-image {
  width: 100%;
  height: 360rpx;
  background: #F5F5F5;
  border-radius: 24rpx;
}

.banner-placeholder {
  height: 360rpx;
  width: calc(100% - 48rpx);
  margin: -16rpx 24rpx 0;
  background: linear-gradient(135deg, #ECFDF5 0%, #D1FAE5 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
}

.placeholder-text {
  font-size: 32rpx;
  color: #00B578;
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
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

/* 限时秒杀横卡（红橙系） */
.seckill-card {
  margin: 16rpx 24rpx 24rpx;
  padding: 20rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #E63946 0%, #FF6B35 100%);
  box-shadow: 0 8rpx 24rpx rgba(230, 57, 70, 0.28);
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
  background: #F5F5F5;
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
  color: #E63946;
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
  background: #E63946;
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
  background: #ECFDF5;
}

.category-icon-default {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #ECFDF5;
  color: #00B578;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}

.category-name {
  font-size: 24rpx;
  color: #111827;
  margin-top: 8rpx;
}

.section {
  background: #FFFFFF;
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
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
  /* 内容区 = 750 - section 左右margin(48) - section 左右padding(48) = 654rpx；
     3 张卡片 + 2 个 gap(16rpx) 正好撑满：(654 - 32) / 3 ≈ 207rpx */
  width: 207rpx;
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
  display: none;
}
</style>
