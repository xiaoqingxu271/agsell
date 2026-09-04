<template>
  <view class="index-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input" @click="onSearchTap">
        <text class="search-icon">🔍</text>
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
          @click="onBannerTap(banner)"
          :show-menu-by-longpress="false"
        />
      </swiper-item>
    </swiper>
    <view v-else class="banner-placeholder">
      <text class="placeholder-text">🍊 新鲜水果 · 🥬有机蔬菜 · 🌾当季特产</text>
    </view>

    <!-- 分类快捷入口 -->
    <view class="category-grid">
      <view
        v-for="cat in categories"
        :key="cat.id"
        class="category-item"
        @click="onCategoryTap(cat)"
      >
        <image v-if="cat.icon" class="category-icon" :src="cat.icon" mode="aspectFill" />
        <text v-else class="category-icon-default">{{ cat.name[0] }}</text>
        <text class="category-name">{{ cat.name }}</text>
      </view>
    </view>

    <!-- 热销推荐 -->
    <view class="section">
      <view class="section-title">🔥 热销推荐</view>
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
      <view class="section-title">🆕 新品推荐</view>
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
import { ref } from 'vue'
import { onMounted } from 'vue'
import ProductCard from '../../components/ProductCard/ProductCard.vue'
import { getBannerList } from '../../api/banner'
import { getCategoryList, getHotProducts, getNewProducts } from '../../api/product'

const banners = ref([])
const categories = ref([])
const hotProducts = ref([])
const newProducts = ref([])

onMounted(async () => {
  await loadBanners()
  await loadCategories()
  await loadHotProducts()
  await loadNewProducts()
  // 仅在已有 token 时才尝试刷新，不强制登录
})

async function loadBanners() {
  const res = await getBannerList()
  if (res.code === 0) banners.value = res.data
}

async function loadCategories() {
  const res = await getCategoryList()
  if (res.code === 0) {
    // 只显示一级分类（parentId=0）
    categories.value = res.data.filter(c => !c.parentId || Number(c.parentId) === 0)
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
  uni.navigateTo({ url: `/pages/category/category?categoryId=${cat.id}` })
}

function onProductTap(product) {
  uni.navigateTo({ url: `/pages/product/product?id=${product.id}` })
}

function onBannerTap(banner) {
  if (banner.link) {
    if (banner.link.startsWith('/product/')) {
      const id = banner.link.replace('/product/', '')
      uni.navigateTo({ url: `/pages/product/product?id=${id}` })
    } else if (banner.link.startsWith('/product/list')) {
      const match = banner.link.match(/categoryId=(\d+)/)
      if (match) uni.navigateTo({ url: `/pages/category/category?categoryId=${match[1]}` })
    }
  }
}

function onSearchTap() {
  uni.showToast({ title: '搜索功能开发中', icon: 'none' })
}
</script>

<style scoped>
.index-page {
  background: #f5f5f5;
  min-height: 100vh;
}

.search-bar {
  background: #4CAF50;
  padding: 20rpx 24rpx;
}

.search-input {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 36rpx;
  height: 72rpx;
  padding: 0 24rpx;
}

.search-icon {
  font-size: 32rpx;
  margin-right: 16rpx;
}

.search-placeholder {
  font-size: 26rpx;
  color: #999;
}

.banner-swiper {
  height: 360rpx;
  width: 100%;
}

.banner-image {
  width: 100%;
  height: 360rpx;
  background: #e8f5e9;
}

.banner-placeholder {
  height: 360rpx;
  width: 100%;
  background: linear-gradient(135deg, #a5d6a7 0%, #66bb6a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-text {
  font-size: 32rpx;
  color: #fff;
  font-weight: bold;
  letter-spacing: 4rpx;
}

.category-grid {
  display: flex;
  flex-wrap: wrap;
  background: #fff;
  padding: 20rpx 0;
}

.category-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20rpx 0;
}

.category-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #e8f5e9;
}

.category-icon-default {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #4CAF50;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: bold;
}

.category-name {
  font-size: 24rpx;
  color: #333;
  margin-top: 8rpx;
}

.section {
  background: #fff;
  margin-top: 20rpx;
  padding: 24rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #333;
  margin-bottom: 20rpx;
}

.product-scroll {
  white-space: nowrap;
}

.product-scroll-inner {
  display: inline-flex;
  gap: 20rpx;
}
</style>
