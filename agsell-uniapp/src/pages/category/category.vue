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
        <view class="product-grid">
          <view
            v-for="product in products"
            :key="product.id"
            class="product-item"
            @click="onProductTap(product)"
          >
            <image
              class="product-img"
              :src="product.mainImage || '/static/default-product.png'"
              mode="aspectFill"
            />
            <text class="product-name">{{ product.name }}</text>
            <view class="product-price-row">
              <text class="product-price">¥{{ product.price }}</text>
              <text v-if="product.originalPrice" class="product-original">¥{{ product.originalPrice }}</text>
            </view>
            <text class="product-sales">已售{{ product.sales }}</text>
          </view>
        </view>

        <!-- 加载中 -->
        <view v-if="loading" class="loading">加载中...</view>
        <view v-if="!hasMore && products.length > 0" class="no-more">没有更多了</view>
        <view v-if="!loading && products.length === 0 && !requesting" class="empty">暂无商品</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onMounted } from 'vue'
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
    // Jackson 序列化后 parentId 为字符串，需转为数字比较
    firstCategories.value = res.data.filter(c => Number(c.parentId) === 0)
    if (firstCategories.value.length > 0 && !currentFirstId.value) {
      currentFirstId.value = Number(firstCategories.value[0].id)
      await loadSecondCategories(Number(firstCategories.value[0].id))
    }
  }
}

async function loadSecondCategories(firstId) {
  const res = await getCategoryList()
  if (res.code === 0) {
    secondCategories.value = res.data.filter(c => Number(c.parentId) === firstId)
    if (secondCategories.value.length > 0 && !currentSecondId.value) {
      currentSecondId.value = Number(secondCategories.value[0].id)
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
  const id = Number(cat.id)
  if (id === currentFirstId.value) return
  currentFirstId.value = id
  currentSecondId.value = null
  products.value = []
  hasMore.value = true
  pageNum.value = 1
  loadSecondCategories(id)
}

function onSecondCategoryTap(cat) {
  currentSecondId.value = cat ? Number(cat.id) : null
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
  uni.navigateTo({ url: `/pages/product/product?id=${product.id}` })
}

function onReachBottom() {
  if (hasMore.value && !loading.value) {
    pageNum.value++
    loadProducts()
  }
}

function onPullDownRefresh() {
  products.value = []
  pageNum.value = 1
  hasMore.value = true
  loadProducts().then(() => uni.stopPullDownRefresh())
}
</script>

<style scoped>
.category-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.category-layout {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.category-sidebar {
  width: 180rpx;
  background: #f0f0f0;
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-item {
  padding: 32rpx 20rpx;
  font-size: 26rpx;
  color: #666;
  text-align: center;
  border-left: 6rpx solid transparent;
}

.sidebar-item.active {
  background: #fff;
  color: #4CAF50;
  border-left-color: #4CAF50;
  font-weight: bold;
}

.category-content {
  flex: 1;
  overflow-y: auto;
  background: #fff;
}

.second-row {
  white-space: nowrap;
  padding: 16rpx 20rpx;
  border-bottom: 1rpx solid #eee;
  background: #fafafa;
}

.second-item {
  display: inline-block;
  padding: 12rpx 28rpx;
  margin-right: 16rpx;
  font-size: 26rpx;
  color: #666;
  background: #fff;
  border-radius: 28rpx;
  border: 1rpx solid #eee;
}

.second-item.active {
  background: #4CAF50;
  color: #fff;
  border-color: #4CAF50;
}

.sort-bar {
  display: flex;
  padding: 16rpx 20rpx;
  border-bottom: 1rpx solid #eee;
  background: #fff;
}

.sort-item {
  flex: 1;
  text-align: center;
  font-size: 26rpx;
  color: #666;
}

.sort-item.active {
  color: #4CAF50;
  font-weight: bold;
}

.sort-arrow {
  font-size: 20rpx;
}

.product-grid {
  display: flex;
  flex-wrap: wrap;
  padding: 16rpx;
}

.product-item {
  width: calc(50% - 16rpx);
  margin: 8rpx;
  background: #fff;
  border-radius: 12rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.05);
}

.product-img {
  width: 100%;
  height: 300rpx;
  background: #f5f5f5;
}

.product-name {
  display: block;
  padding: 12rpx 16rpx;
  font-size: 26rpx;
  color: #333;
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
  color: #FF9800;
  font-size: 30rpx;
  font-weight: bold;
}

.product-price::before { content: '¥'; font-size: 22rpx; }

.product-original {
  color: #ccc;
  font-size: 22rpx;
  text-decoration: line-through;
  margin-left: 8rpx;
}

.product-sales {
  display: block;
  padding: 8rpx 16rpx;
  font-size: 20rpx;
  color: #999;
}

.loading, .no-more, .empty {
  text-align: center;
  padding: 40rpx;
  color: #999;
  font-size: 26rpx;
}
</style>
