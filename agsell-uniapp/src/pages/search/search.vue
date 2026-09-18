<template>
  <view class="search-page">
    <!-- 搜索栏 -->
    <view class="search-header">
      <view class="search-input-wrap">
        <image class="search-icon" src="/static/icon-search.png" mode="aspectFit" alt="搜索" />
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索商品"
          placeholder-class="search-placeholder"
          confirm-type="search"
          focus
          @confirm="onSearch"
          @clear="onClearKeyword"
        />
        <text v-if="keyword" class="clear-btn" @click="onClearKeyword">×</text>
      </view>
      <text class="search-btn" @click="onSearch">搜索</text>
    </view>

    <!-- 未搜索：历史 + 热词 -->
    <view v-if="!searched" class="suggest-area">
      <view v-if="history.length" class="block">
        <view class="block-header">
          <text class="block-title">搜索历史</text>
          <text class="block-action" @click="onClearHistory">清空</text>
        </view>
        <view class="tag-wrap">
          <text v-for="(h, i) in history" :key="'h' + i" class="tag" @click="onTagTap(h)">{{ h }}</text>
        </view>
      </view>
      <view v-if="hotWords.length" class="block">
        <view class="block-header">
          <text class="block-title">热门搜索</text>
        </view>
        <view class="tag-wrap">
          <text v-for="(w, i) in hotWords" :key="'w' + i" class="tag hot" @click="onTagTap(w)">{{ w }}</text>
        </view>
      </view>
      <view v-if="!history.length && !hotWords.length" class="empty-tip">
        <text>输入关键词，搜索你想要的农产品</text>
      </view>
    </view>

    <!-- 已搜索：排序 + 结果 -->
    <view v-else class="result-area">
      <view class="sort-bar">
        <text :class="['sort-item', sortBy === '' ? 'active' : '']" @click="onSort('')">相关度</text>
        <text :class="['sort-item', sortBy === 'hot' ? 'active' : '']" @click="onSort('hot')">销量</text>
        <text :class="['sort-item', sortBy === 'price_asc' ? 'active' : '']" @click="onSort('price_asc')">价格</text>
      </view>

      <view v-if="list.length" class="product-list">
        <view v-for="item in list" :key="item.id" class="product-card" hover-class="product-card-hover" @click="onProductTap(item)">
          <image class="product-image" :src="item.mainImage || '/static/default-product.png'" mode="aspectFill" lazy-load :alt="item.name" />
          <view class="product-info">
            <rich-text class="product-name" :nodes="item.nameHighlight || item.name"></rich-text>
            <rich-text v-if="item.subtitleHighlight" class="product-subtitle" :nodes="item.subtitleHighlight"></rich-text>
            <view class="product-footer">
              <text class="price">¥{{ item.price }}</text>
              <text class="sales">已售{{ item.sales || 0 }}</text>
            </view>
          </view>
        </view>
        <view v-if="loading" class="load-tip">加载中...</view>
        <view v-else-if="noMore && list.length" class="load-tip">没有更多了</view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <text class="empty-icon">🔍</text>
        <text class="empty-text">没有找到「{{ keyword }}」相关商品</text>
        <text class="empty-sub">换个关键词试试</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { searchProducts, getHotWords } from '../../api/product'

const HISTORY_KEY = 'searchHistory'
const MAX_HISTORY = 10

const keyword = ref('')
const history = ref([])
const hotWords = ref([])
const searched = ref(false)
const list = ref([])
const sortBy = ref('')
const total = ref(0)
const loading = ref(false)
const noMore = ref(false)

let pageNum = 1
const PAGE_SIZE = 12

onLoad(() => {
  loadHistory()
  loadHotWords()
})

onReachBottom(() => {
  if (searched.value && !loading.value && !noMore.value) {
    doSearch(false)
  }
})

/** 加载本地搜索历史 */
function loadHistory() {
  try {
    history.value = uni.getStorageSync(HISTORY_KEY) || []
  } catch (e) {
    history.value = []
  }
}

/** 保存本地搜索历史（最新在前，去重，最多 10 条） */
function saveHistory(word) {
  const next = [word, ...history.value.filter((w) => w !== word)]
  history.value = next.slice(0, MAX_HISTORY)
  try {
    uni.setStorageSync(HISTORY_KEY, history.value)
  } catch (e) {
    console.warn('保存搜索历史失败', e)
  }
}

/** 加载热门搜索词 */
async function loadHotWords() {
  try {
    const res = await getHotWords(10)
    if (res.code === 0 && Array.isArray(res.data)) {
      hotWords.value = res.data
    }
  } catch (e) {
    console.warn('加载热词失败', e)
  }
}

/** 执行搜索 */
async function doSearch(reset) {
  const kw = keyword.value.trim()
  if (!kw) {
    uni.showToast({ title: '请输入搜索关键词', icon: 'none' })
    return
  }
  if (reset) {
    pageNum = 1
    list.value = []
    noMore.value = false
    searched.value = true
  }
  loading.value = true
  try {
    const res = await searchProducts({
      keyword: kw,
      sortBy: sortBy.value,
      pageNum,
      pageSize: PAGE_SIZE
    })
    if (res.code === 0 && res.data) {
      const records = res.data.records || []
      list.value = reset ? records : [...list.value, ...records]
      total.value = res.data.total || 0
      noMore.value = list.value.length >= total.value
      pageNum += 1
    } else {
      uni.showToast({ title: res.message || '搜索失败', icon: 'none' })
    }
  } catch (e) {
    console.warn('搜索失败', e)
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    uni.showToast({ title: '请输入搜索关键词', icon: 'none' })
    return
  }
  saveHistory(kw)
  doSearch(true)
}

function onTagTap(word) {
  keyword.value = word
  saveHistory(word)
  doSearch(true)
}

function onClearKeyword() {
  keyword.value = ''
  searched.value = false
  list.value = []
  noMore.value = false
}

function onClearHistory() {
  history.value = []
  try {
    uni.removeStorageSync(HISTORY_KEY)
  } catch (e) {
    console.warn('清空历史失败', e)
  }
}

function onSort(sort) {
  if (sortBy.value === sort) return
  sortBy.value = sort
  doSearch(true)
}

function onProductTap(item) {
  if (!item || !item.id) {
    uni.showToast({ title: '商品ID无效', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/product?id=${item.id}` })
}
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  background: #F5F5F5;
}

/* 搜索栏 */
.search-header {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #FFFFFF;
  border-bottom: 1rpx solid #E5E7EB;
  position: sticky;
  top: 0;
  z-index: 10;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  height: 72rpx;
  background: #F5F5F5;
  border-radius: 36rpx;
  padding: 0 24rpx;
}

.search-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: #111827;
  height: 72rpx;
  line-height: 72rpx;
}

.search-placeholder {
  color: #9CA3AF;
}

.clear-btn {
  font-size: 36rpx;
  color: #9CA3AF;
  padding: 0 8rpx;
}

.search-btn {
  margin-left: 20rpx;
  font-size: 28rpx;
  color: #00B578;
  font-weight: 600;
}

/* 未搜索区 */
.suggest-area {
  padding: 24rpx;
}

.block {
  margin-bottom: 32rpx;
}

.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.block-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}

.block-action {
  font-size: 24rpx;
  color: #4B5563;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.tag {
  font-size: 26rpx;
  color: #374151;
  background: #FFFFFF;
  border: 1rpx solid #E5E7EB;
  border-radius: 32rpx;
  padding: 10rpx 28rpx;
  line-height: 1.4;
}

.tag.hot {
  color: #00B578;
  background: #ECFDF5;
  border-color: #D1FAE5;
}

.empty-tip {
  padding: 120rpx 0;
  text-align: center;
  color: #9CA3AF;
  font-size: 26rpx;
}

/* 结果区 */
.result-area {
  padding-bottom: 32rpx;
}

.sort-bar {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  padding: 0 24rpx;
  border-bottom: 1rpx solid #E5E7EB;
  position: sticky;
  top: 112rpx;
  z-index: 9;
}

.sort-item {
  font-size: 26rpx;
  color: #4B5563;
  padding: 20rpx 0;
  margin-right: 40rpx;
  position: relative;
}

.sort-item.active {
  color: #00B578;
  font-weight: 600;
}

.sort-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 6rpx;
  border-radius: 6rpx 6rpx 0 0;
  background: #00B578;
}

.product-list {
  padding: 16rpx 24rpx 0;
}

.product-card {
  display: flex;
  background: #FFFFFF;
  border: 1rpx solid #E5E7EB;
  border-radius: 24rpx;
  overflow: hidden;
  margin-bottom: 16rpx;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.product-card-hover {
  transform: scale(0.98);
}

.product-image {
  width: 200rpx;
  height: 200rpx;
  flex-shrink: 0;
  background: #F5F5F5;
}

.product-info {
  flex: 1;
  min-width: 0;
  padding: 20rpx;
  display: flex;
  flex-direction: column;
}

.product-name {
  font-size: 28rpx;
  color: #111827;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-subtitle {
  font-size: 24rpx;
  color: #4B5563;
  margin-top: 8rpx;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-footer {
  display: flex;
  align-items: baseline;
  margin-top: auto;
  padding-top: 12rpx;
}

.price {
  color: #E63946;
  font-size: 34rpx;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.sales {
  color: #4B5563;
  font-size: 22rpx;
  margin-left: auto;
}

.load-tip {
  text-align: center;
  color: #9CA3AF;
  font-size: 24rpx;
  padding: 24rpx 0;
}

/* 空态 */
.empty-state {
  padding: 140rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  font-size: 72rpx;
  margin-bottom: 20rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #374151;
}

.empty-sub {
  font-size: 24rpx;
  color: #9CA3AF;
  margin-top: 12rpx;
}
</style>
