<template>
  <view class="category-page">
    <view class="category-layout">
      <!-- 左侧一级分类 -->
      <scroll-view scroll-y class="category-sidebar">
        <view
          v-for="cat in firstCategories"
          :key="cat.id"
          class="sidebar-item"
          :class="{ active: currentFirstId === cat.id }"
          @click="onFirstCategoryTap(cat)"
          role="button"
          :aria-label="cat.name"
        >
          {{ cat.name }}
        </view>
      </scroll-view>

      <!-- 右侧内容区 -->
      <view class="category-content">
        <!-- 二级分类 -->
        <scroll-view scroll-x class="second-row">
          <view
            v-for="cat in secondCategories"
            :key="cat.id"
            class="second-item"
            :class="{ active: currentSecondId === cat.id }"
            @click="onSecondCategoryTap(cat)"
          >
            {{ cat.name }}
          </view>
          <view
            v-if="secondCategories.length === 0"
            class="second-item all-category"
            :class="{ active: !currentSecondId }"
            @click="onSecondCategoryTap(null)"
          >
            全部
          </view>
        </scroll-view>

        <!-- 排序栏 -->
        <view class="sort-bar">
          <view
            v-for="item in sortOptions"
            :key="item.value"
            class="sort-item"
            :class="{ active: sortBy === item.value }"
            @click="onSortTap(item.value)"
          >
            {{ item.label }}
            <text v-if="sortBy === item.value" class="sort-arrow">↑</text>
          </view>
        </view>

        <!-- 商品列表 -->
        <scroll-view scroll-y class="product-list-scroll" @scrolltolower="onReachBottom">
          <view class="product-grid">
            <view
              v-for="product in products"
              :key="product.id"
              class="product-item"
              @click="onProductTap(product)"
            >
              <view class="product-img-wrap">
                <image
                  class="product-img"
                  :src="product.mainImage || '/static/default-product.png'"
                  mode="aspectFill"
                  :alt="product.name"
                />
              </view>
              <text class="product-name">{{ product.name }}</text>
              <view class="product-price-row">
                <text class="product-price">¥{{ product.price }}</text>
                <text v-if="product.originalPrice" class="product-original">¥{{ product.originalPrice }}</text>
              </view>
              <text class="product-sales">已售{{ product.sales || 0 }}</text>
            </view>
          </view>

          <!-- 加载中 -->
          <view v-if="loading" class="loading">加载中...</view>
          <view v-if="!hasMore && products.length > 0" class="no-more">没有更多了</view>
          <view v-if="!loading && products.length === 0 && !requesting" class="empty">暂无商品</view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCategoryList, getProductList } from '../../api/product'

const firstCategories = ref([])
const secondCategories = ref([])
const products = ref([])
const currentFirstId = ref(null)
const currentSecondId = ref(null)
const sortBy = ref('')
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const hasMore = ref(true)
const requesting = ref(false)

const sortOptions = [
  { label: '综合', value: '' },
  { label: '销量', value: 'hot' },
  { label: '价格↑', value: 'price_asc' },
  { label: '价格↓', value: 'price_desc' },
  { label: '新品', value: 'new' }
]

onMounted(async () => {
  await loadCategories()
})

async function loadCategories() {
  const res = await getCategoryList()
  if (res.code === 0) {
    firstCategories.value = res.data.filter(c => String(c.parentId) === '0')
    if (firstCategories.value.length > 0 && !currentFirstId.value) {
      currentFirstId.value = String(firstCategories.value[0].id)
      await loadSecondCategories(String(firstCategories.value[0].id))
    }
  }
}

async function loadSecondCategories(firstId) {
  const res = await getCategoryList()
  if (res.code === 0) {
    secondCategories.value = res.data.filter(c => String(c.parentId) === String(firstId))
    if (secondCategories.value.length > 0 && !currentSecondId.value) {
      currentSecondId.value = String(secondCategories.value[0].id)
    }
    pageNum.value = 1
    products.value = []
    hasMore.value = true
    await loadProducts()
  }
}

async function loadProducts() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  requesting.value = true

  const categoryId = currentSecondId.value || currentFirstId.value
  const res = await getProductList({
    categoryId,
    sortBy: sortBy.value,
    pageNum: pageNum.value,
    pageSize: pageSize.value
  })

  if (res.code === 0) {
    const { records, current, pages } = res.data
    if (pageNum.value === 1) {
      products.value = records
    } else {
      products.value = [...products.value, ...records]
    }
    hasMore.value = Number(current) < Number(pages)
  }
  loading.value = false
  requesting.value = false
}

function onFirstCategoryTap(cat) {
  const id = String(cat.id)
  if (id === currentFirstId.value) return
  currentFirstId.value = id
  currentSecondId.value = null
  products.value = []
  hasMore.value = true
  pageNum.value = 1
  loadSecondCategories(id)
}

function onSecondCategoryTap(cat) {
  currentSecondId.value = cat ? String(cat.id) : null
  products.value = []
  hasMore.value = true
  pageNum.value = 1
  loadProducts()
}

function onSortTap(value) {
  sortBy.value = sortBy.value === value ? '' : value
  products.value = []
  hasMore.value = true
  pageNum.value = 1
  loadProducts()
}

function onProductTap(product) {
  const id = String(product?.id || '')
  if (!id || id === 'undefined' || id === 'null') {
    uni.showToast({ title: '商品ID无效', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/product?id=${id}` })
}

function onReachBottom() {
  if (hasMore.value && !loading.value) {
    pageNum.value++
    loadProducts()
  }
}
</script>

<style scoped>
.category-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F0FDF4;
}

.category-layout {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.category-sidebar {
  width: 180rpx;
  background: #FFFFFF;
  overflow-y: auto;
  flex-shrink: 0;
  border-right: 1px solid #E5E7EB;
}

.sidebar-item {
  padding: 32rpx 16rpx;
  font-size: 26rpx;
  color: #6B7280;
  text-align: center;
  border-left: 6rpx solid transparent;
  min-height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.sidebar-item.active {
  background: #F0FDF4;
  color: #15803D;
  border-left-color: #15803D;
  font-weight: 600;
}

.category-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #F0FDF4;
  overflow: hidden;
}

.second-row {
  white-space: nowrap;
  padding: 16rpx 20rpx;
  border-bottom: 1px solid #E5E7EB;
  background: #FFFFFF;
  flex-shrink: 0;
}

.second-item {
  display: inline-block;
  padding: 12rpx 28rpx;
  margin-right: 16rpx;
  font-size: 26rpx;
  color: #6B7280;
  background: #FFFFFF;
  border-radius: 44rpx;
  border: 1px solid #BBF7D0;
  min-height: 60rpx;
  line-height: 60rpx;
  box-sizing: border-box;
}

.second-item.active {
  background: #15803D;
  color: #FFFFFF;
  border-color: #15803D;
}

.sort-bar {
  display: flex;
  padding: 16rpx 20rpx;
  border-bottom: 1px solid #E5E7EB;
  background: #FFFFFF;
  flex-shrink: 0;
}

.sort-item {
  flex: 1;
  text-align: center;
  font-size: 26rpx;
  color: #6B7280;
  min-height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sort-item.active {
  color: #15803D;
  font-weight: 600;
}

.sort-arrow {
  font-size: 20rpx;
  margin-left: 4rpx;
}

.product-list-scroll {
  flex: 1;
  height: 0;
}

.product-grid {
  display: flex;
  flex-wrap: wrap;
  padding: 16rpx;
  gap: 16rpx;
}

.product-item {
  width: calc(50% - 8rpx);
  background: #FFFFFF;
  border-radius: 24rpx;
  overflow: hidden;
  border: 1px solid #BBF7D0;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
  min-height: 88rpx;
}

.product-img-wrap {
  width: 100%;
  padding-top: 100%;
  position: relative;
  overflow: hidden;
  background: #F0FDF4;
  border-radius: 12rpx 12rpx 0 0;
}

.product-img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.product-name {
  display: block;
  padding: 12rpx 16rpx;
  font-size: 26rpx;
  color: #1F2937;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-price-row {
  display: flex;
  align-items: baseline;
  padding: 0 16rpx;
}

.product-price {
  color: #A16207;
  font-size: 32rpx;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.product-price::before { content: '¥'; font-size: 22rpx; }

.product-original {
  color: #9CA3AF;
  font-size: 22rpx;
  text-decoration: line-through;
  margin-left: 8rpx;
}

.product-sales {
  display: block;
  padding: 8rpx 16rpx 16rpx;
  font-size: 22rpx;
  color: #6B7280;
}

.loading, .no-more, .empty {
  text-align: center;
  padding: 40rpx;
  color: #9CA3AF;
  font-size: 26rpx;
}
</style>
