<template>
  <view class="category-page">
    <!-- 顶部搜索 + 扫码（白底头部） -->
    <view class="hd">
      <view class="srch" @click="onSearchTap" role="button" aria-label="搜索商品">
        <wd-icon name="search" size="28rpx" custom-class="srch-ic" />
        <text class="srch-ph">搜索分类内商品</text>
      </view>
      <view class="hd-ic" @click="onSearchTap" role="button" aria-label="扫码搜索">
        <wd-icon name="scan" size="36rpx" custom-class="hd-scan" />
      </view>
    </view>

    <view class="category-layout">
      <!-- 左侧一级导轨 -->
      <scroll-view scroll-y class="rail">
        <view
          v-for="cat in firstCategories"
          :key="cat.id"
          class="rail-item"
          :class="{ active: currentFirstId === cat.id }"
          @click="onFirstCategoryTap(cat)"
          role="button"
          :aria-label="cat.name"
        >
          <text class="rail-name">{{ cat.name }}</text>
        </view>
      </scroll-view>

      <!-- 右侧内容区 -->
      <view class="cmain">
        <scroll-view scroll-y class="cmain-scroll" @scrolltolower="onReachBottom">
          <!-- Banner 浅红条 -->
          <view class="cban">
            <view class="cban-txt">
              <text class="cban-title">{{ currentFirstName }}</text>
              <text class="cban-sub">优选产地 · 新鲜直达</text>
            </view>
            <wd-icon name="bags" size="68rpx" custom-class="cban-ic" />
          </view>

          <!-- 排序 chips -->
          <scroll-view scroll-x class="cts">
            <view
              v-for="item in sortOptions"
              :key="item.value"
              class="chip"
              :class="{ on: sortBy === item.value }"
              @click="onSortTap(item.value)"
            >
              {{ item.label }}
            </view>
          </scroll-view>

          <!-- 当前子类名 + 二级分类图标网格 -->
          <text v-if="currentSecondName" class="cgrp-t">{{ currentSecondName }}</text>
          <view class="cgrid">
            <view
              v-for="cat in secondCategories"
              :key="cat.id"
              class="cgrid-item"
              :class="{ on: currentSecondId === cat.id }"
              @click="onSecondCategoryTap(cat)"
            >
              <view class="cgrid-im">
                <wd-icon name="goods" size="48rpx" custom-class="cgrid-ic" />
              </view>
              <text class="cgrid-name">{{ cat.name }}</text>
            </view>
            <view
              v-if="secondCategories.length === 0"
              class="cgrid-item"
              :class="{ on: !currentSecondId }"
              @click="onSecondCategoryTap(null)"
            >
              <view class="cgrid-im">
                <wd-icon name="list" size="48rpx" custom-class="cgrid-ic" />
              </view>
              <text class="cgrid-name">全部</text>
            </view>
          </view>

          <view class="sep"></view>

          <!-- 商品单列横向列表 -->
          <view class="clist">
            <view
              v-for="product in products"
              :key="product.id"
              class="li"
              @click="onProductTap(product)"
            >
              <view class="li-thumb">
                <image
                  v-if="product.mainImage"
                  class="li-img"
                  :src="product.mainImage"
                  mode="aspectFill"
                  lazy-load
                  :alt="product.name"
                />
                <wd-icon v-else name="picture" size="72rpx" custom-class="li-ph-ic" />
              </view>
              <view class="li-in">
                <text class="li-nm">{{ product.name }}</text>
                <text class="li-sp">{{ product.subtitle || `已售 ${product.sales || 0}` }}</text>
                <view class="li-bt">
                  <view class="price">
                    <text class="price-sym">¥</text>
                    <text class="price-num">{{ product.price }}</text>
                    <text v-if="product.originalPrice" class="price-origin">{{ product.originalPrice }}</text>
                  </view>
                  <view class="add" @click.stop="onQuickAdd(product)" role="button" aria-label="加入购物车">
                    <wd-icon name="add" size="24rpx" custom-class="add-ic" />
                  </view>
                </view>
              </view>
            </view>
          </view>

          <!-- 状态提示 -->
          <view v-if="loading" class="hint">加载中...</view>
          <view v-if="!hasMore && products.length > 0" class="hint">— 以上为「{{ currentFirstName }}」全部商品 —</view>
          <view v-if="!loading && products.length === 0 && !requesting" class="empty">暂无商品</view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { getCategoryList, getProductList } from '../../api/product'
import { addToCart } from '../../api/cart'
import { isLoggedIn, wxLogin, setPlatformTitle } from '../../utils/request'

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

// 纯展示：当前选中一级分类名，用于右侧 Banner 文案（无匹配则为空）
const currentFirstName = computed(() => {
  const hit = firstCategories.value.find((c) => String(c.id) === String(currentFirstId.value))
  return hit ? hit.name : '分类精选'
})

// 纯展示：当前选中子类名，作为图标网格上方的小标题
const currentSecondName = computed(() => {
  const hit = secondCategories.value.find((c) => String(c.id) === String(currentSecondId.value))
  return hit ? hit.name : ''
})

onMounted(async () => {
  setPlatformTitle()
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

/** 列表右下角红色加购，与首页/详情页走同一接口 */
async function onQuickAdd(product) {
  if (!isLoggedIn()) {
    const success = await wxLogin()
    if (!success) return
  }
  const res = await addToCart({ productId: product.id, specId: null, quantity: 1 })
  if (res.code === 0) {
    uni.showToast({ title: '已加入购物车', icon: 'success' })
  } else {
    uni.showToast({ title: res.message || '操作失败', icon: 'none' })
  }
}

function onReachBottom() {
  if (hasMore.value && !loading.value) {
    pageNum.value++
    loadProducts()
  }
}

function onSearchTap() {
  uni.navigateTo({ url: '/pages/search/search' })
}
</script>

<style lang="scss" scoped>
.category-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $uni-bg-color-grey;
}

/* ===== 顶部搜索头部（白底 + 扫码） ===== */
.hd {
  background: $uni-bg-color;
  padding: 12rpx 24rpx 20rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  flex-shrink: 0;
}

.srch {
  flex: 1;
  min-width: 0;
  height: 64rpx;
  background: $uni-bg-color-muted;
  border: 2rpx solid #E8E9EC;
  border-radius: 32rpx;
  display: flex;
  align-items: center;
  padding: 0 20rpx;
  gap: 12rpx;
}

.hd-ic {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

:deep(.hd-scan) {
  color: $uni-text-color-grey;
}

:deep(.srch-ic) {
  color: $uni-text-color-placeholder;
  flex-shrink: 0;
}

.srch-ph {
  flex: 1;
  font-size: 24rpx;
  color: $uni-text-color-placeholder;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.category-layout {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== 左侧一级导轨 ===== */
.rail {
  width: 164rpx;
  background: $uni-bg-color-img;
  flex-shrink: 0;
  overflow-y: auto;
}

.rail-item {
  min-height: 104rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16rpx 12rpx;
  box-sizing: border-box;
  transition: background 150ms ease-out, color 150ms ease-out;
}

.rail-name {
  font-size: 24rpx;
  color: $uni-text-color-grey;
  text-align: center;
  line-height: 34rpx;
}

.rail-item.active {
  background: $uni-bg-color;
  box-shadow: inset 6rpx 0 0 $uni-color-primary;

  .rail-name {
    color: $uni-color-primary;
    font-weight: 600;
  }
}

/* ===== 右侧内容区 ===== */
.cmain {
  flex: 1;
  min-width: 0;
  background: $uni-bg-color;
  overflow: hidden;
}

.cmain-scroll {
  height: 100%;
}

/* Banner 浅红渐变条 */
.cban {
  display: flex;
  align-items: center;
  gap: 20rpx;
  height: 124rpx;
  margin: 20rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border-radius: $uni-border-radius-base;
  background: linear-gradient(100deg, #FFF4F3, $uni-color-primary-50);
  border: 2rpx solid $uni-color-primary-100;
}

.cban-txt {
  flex: 1;
  min-width: 0;
}

.cban-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: $uni-color-primary;
}

.cban-sub {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $uni-text-color-grey;
}

:deep(.cban-ic) {
  color: $uni-color-primary;
  opacity: 0.5;
  flex-shrink: 0;
}

/* 排序 chips */
.cts {
  white-space: nowrap;
  padding: 0 20rpx 20rpx;
}

.chip {
  display: inline-block;
  height: 52rpx;
  line-height: 52rpx;
  padding: 0 22rpx;
  margin-right: 12rpx;
  border-radius: $uni-border-radius-pill;
  background: $uni-bg-color-grey;
  font-size: 22rpx;
  color: $uni-text-color-grey;
}

.chip.on {
  background: $uni-color-primary-50;
  color: $uni-color-primary;
  font-weight: 600;
}

/* 子类小标题 */
.cgrp-t {
  display: block;
  padding: 0 20rpx 12rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: $uni-text-color-grey;
}

/* 二级分类图标网格 */
.cgrid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24rpx 0;
  padding: 4rpx 20rpx 24rpx;
}

.cgrid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.cgrid-im {
  width: 92rpx;
  height: 92rpx;
  border-radius: 12rpx;
  background: $uni-bg-color-img;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.cgrid-ic) {
  color: $uni-color-img-icon;
}

.cgrid-item.on .cgrid-im {
  background: $uni-color-primary-50;
  box-shadow: inset 0 0 0 2rpx $uni-color-primary;

  :deep(.cgrid-ic) {
    color: $uni-color-primary;
  }
}

.cgrid-name {
  font-size: 20rpx;
  color: $uni-text-color-grey;
}

.cgrid-item.on .cgrid-name {
  color: $uni-color-primary;
  font-weight: 600;
}

/* 分割线 */
.sep {
  height: 1rpx;
  background: $uni-border-color-divider;
  margin: 0 20rpx;
}

/* 商品单列横向列表 */
.clist {
  padding: 0 20rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.li {
  display: flex;
  gap: 18rpx;
  transition: opacity 150ms ease-out;
}

.li:active {
  opacity: 0.85;
}

.li-thumb {
  width: 156rpx;
  height: 156rpx;
  border-radius: 12rpx;
  background: $uni-bg-color-img;
  flex-shrink: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.li-ph-ic) {
  color: $uni-color-img-icon;
}

.li-img {
  width: 100%;
  height: 100%;
}

.li-in {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.li-nm {
  font-size: 24rpx;
  line-height: 34rpx;
  height: 68rpx;
  color: $uni-text-color;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.li-sp {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $uni-text-color-3;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.li-bt {
  margin-top: auto;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.price {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  color: $uni-color-primary;
}

.price-sym {
  font-size: 22rpx;
  font-weight: 700;
}

.price-num {
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -1rpx;
}

.price-origin {
  font-size: 22rpx;
  color: $uni-text-color-3;
  text-decoration: line-through;
  margin-left: 6rpx;
}

.add {
  width: 44rpx;
  height: 44rpx;
  border-radius: 22rpx;
  background: $uni-color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

:deep(.add-ic) {
  color: $uni-text-color-inverse;
}

/* 状态提示 */
.hint {
  text-align: center;
  padding: 12rpx 0 24rpx;
  font-size: 20rpx;
  color: $uni-text-color-disable;
}

.empty {
  text-align: center;
  padding: 80rpx 24rpx;
  font-size: 26rpx;
  color: $uni-text-color-grey;
}
</style>
