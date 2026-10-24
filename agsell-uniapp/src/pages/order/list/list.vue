<template>
  <view class="order-list-page">
    <!-- 订单搜索（P1 §4.6.2）：按订单号或商品名模糊匹配 -->
    <view class="search-bar">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索订单号 / 商品名"
        placeholder-class="search-placeholder"
        confirm-type="search"
        @confirm="onSearch"
      />
      <view class="search-btn" role="button" aria-label="搜索订单" @click="onSearch">搜索</view>
    </view>

    <view class="tabs">
      <view
        v-for="(tab, index) in tabs"
        :key="tab.value"
        class="tab-item"
        :class="{ active: currentTab === index }"
        @click="onTabChange(index)"
        role="tab"
        :aria-selected="currentTab === index"
      >
        {{ tab.label }}
        <text v-if="tab.count > 0" class="tab-count">{{ tab.count }}</text>
      </view>
    </view>

    <scroll-view
      scroll-y
      class="order-scroll"
      @scrolltolower="onReachBottom"
      refresher-enabled
      @refresherrefresh="onPullDownRefresh"
      :refresher-triggered="refreshing"
    >
      <view v-if="orders.length === 0 && !loading" class="empty-orders">
        <text>暂无订单</text>
      </view>

      <view
        v-for="order in orders"
        :key="order.orderNo"
        class="order-card card"
        @click="onOrderTap(order)"
      >
        <view class="order-header">
          <text class="order-no">订单号：{{ order.orderNo }}</text>
          <text class="order-status" :class="getStatusClass(order.status)">
            {{ order.statusText }}
          </text>
        </view>

        <view v-for="item in order.items" :key="item.productId" class="order-product">
          <image class="product-img" :src="item.productImage || '/static/default-product.png'" mode="aspectFill" lazy-load :alt="item.productName" />
          <view class="product-info">
            <text class="product-name">{{ item.productName }}</text>
            <text v-if="item.specName" class="product-spec">{{ item.specName }}</text>
          </view>
          <text class="product-qty">×{{ item.quantity }}</text>
        </view>

        <view class="order-footer">
          <text class="order-time">{{ formatDate(order.createTime) }}</text>
          <text class="order-amount">共{{ order.itemCount }}件　合计：<text class="amount-highlight">¥{{ order.payAmount }}</text></text>
          <view class="order-actions">
            <view
              v-if="order.status === 0"
              class="action-btn btn-ghost btn-danger"
              @click.stop="onCancelOrder(order)"
            >取消订单</view>
            <view
              v-if="order.status === 0"
              class="action-btn btn-primary"
              @click.stop="onPayOrder(order)"
            >去支付</view>
            <view
              v-if="order.status === 2"
              class="action-btn btn-primary"
              @click.stop="onConfirmReceive(order)"
            >确认收货</view>
            <view
              v-if="order.status === 3"
              class="action-btn btn-primary"
              @click.stop="onReview(order)"
            >评价</view>
            <view
              v-if="order.status === 3"
              class="action-btn btn-ghost"
              @click.stop="onRepurchase(order)"
            >再次购买</view>
          </view>
        </view>
      </view>

      <view v-if="loading" class="loading">加载中...</view>
      <view v-if="!hasMore && orders.length > 0" class="no-more">没有更多了</view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getOrderList, cancelOrder, confirmReceive } from '../../../api/order'
import { formatDate } from '../../../utils/format'

const tabs = [
  { label: '全部', value: null, count: 0 },
  { label: '待付款', value: 0, count: 0 },
  { label: '待发货', value: 1, count: 0 },
  { label: '待收货', value: 2, count: 0 },
  { label: '已完成', value: 3, count: 0 }
]
const currentTab = ref(0)
const orders = ref([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const hasMore = ref(true)
const refreshing = ref(false)

const statusClassMap = {
  0: 'tag-warning',
  1: 'tag-primary',
  2: 'tag-success',
  3: 'tag-info',
  4: 'tag-info',
  5: 'tag-danger',
  6: 'tag-success'
}

function getStatusClass(status) {
  return statusClassMap[status] || 'tag-info'
}

onLoad((options) => {
  const statusStr = options?.status
  if (statusStr !== undefined && statusStr !== null && statusStr !== '') {
    const statusNum = Number(statusStr)
    const tabIndex = tabs.findIndex(t => t.value === statusNum)
    if (tabIndex > 0) {
      currentTab.value = tabIndex
    }
  }
})

onShow(() => {
  // 每次回到列表页都刷新第一页，保证售后等状态变更后列表与详情一致
  if (loading.value) return
  pageNum.value = 1
  orders.value = []
  hasMore.value = true
  loadOrders()
})

function onSearch() {
  pageNum.value = 1
  orders.value = []
  hasMore.value = true
  loadOrders()
}

async function loadOrders() {
  if (loading.value) return
  loading.value = true
  const currentTabValue = tabs[currentTab.value].value
  const res = await getOrderList({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    status: currentTabValue,
    keyword: keyword.value.trim() || undefined
  })
  if (res.code === 0) {
    const { records, current, pages } = res.data
    orders.value = pageNum.value === 1 ? records : [...orders.value, ...records]
    hasMore.value = current < pages
    // 第一页加载完成后，只把"当前正在查看的这个状态"记为已读基准；
    // 其他状态的角标不动，避免用户只看了待付款就连带把待发货等角标也清掉。
    if (pageNum.value === 1) {
      await markOrderStatusViewed(currentTabValue)
    }
  }
  loading.value = false
  refreshing.value = false
}

/**
 * 把指定状态的订单总数写为"已读基准"，回到"我的"页时该状态未读数归零。
 * 只处理用户实际查看的那个状态；"全部"tab（status=null）不标记任何单一状态。
 * 必须 await：确保返回上一页读缓存前，写入已落盘。
 */
async function markOrderStatusViewed(status) {
  if (status === null || status === undefined) return
  try {
    const res = await getOrderList({ pageNum: 1, pageSize: 1, status })
    if (res && res.code === 0) {
      uni.setStorageSync('order_last_total_' + status, res.data?.total || 0)
      // 立即通知"我的"页把该状态角标清零，返回时无需等 onShow 重新拉接口
      uni.$emit('orderBadgeCleared', status)
    }
  } catch (e) {
    // 失败静默，不影响列表展示
  }
}

function onTabChange(index) {
  if (index === currentTab.value) return
  currentTab.value = index
  pageNum.value = 1
  orders.value = []
  hasMore.value = true
  loadOrders()
}

function onReachBottom() {
  if (hasMore.value && !loading.value) {
    pageNum.value++
    loadOrders()
  }
}

function onPullDownRefresh() {
  pageNum.value = 1
  orders.value = []
  hasMore.value = true
  refreshing.value = true
  loadOrders()
}

function onOrderTap(order) {
  uni.navigateTo({ url: `/pages/order/detail/detail?orderNo=${order.orderNo}` })
}

async function onCancelOrder(order) {
  uni.showModal({
    title: '取消订单',
    content: '确定要取消该订单吗？',
    success: async (res) => {
      if (res.confirm) {
        await cancelOrder(order.orderNo, '用户主动取消')
        uni.showToast({ title: '订单已取消', icon: 'success' })
        loadOrders()
      }
    }
  })
}

function onPayOrder(order) {
  // 跳转支付页选择支付方式（支付宝/微信），由支付页完成模拟支付
  uni.navigateTo({
    url: `/pages/order/pay/pay?orderNo=${order.orderNo}&payAmount=${order.payAmount}`
  })
}

async function onConfirmReceive(order) {
  uni.showModal({
    title: '确认收货',
    content: '确认已收到商品？',
    success: async (res) => {
      if (res.confirm) {
        await confirmReceive(order.orderNo)
        uni.showToast({ title: '确认收货成功', icon: 'success' })
        loadOrders()
      }
    }
  })
}

function onReview(order) {
  uni.navigateTo({
    url: `/pages/order/detail/detail?orderNo=${order.orderNo}`
  })
}

function onRepurchase(order) {
  uni.navigateTo({ url: `/pages/order/detail/detail?orderNo=${order.orderNo}` })
}
</script>

<style scoped>
.order-list-page {
  min-height: 100vh;
  background: #F4F5F7;
}

.tabs {
  display: flex;
  background: #FFFFFF;
  border-bottom: 1rpx solid #E3E5E8;
  position: sticky;
  top: 0;
  z-index: 10;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 28rpx 0;
  font-size: 28rpx;
  color: #666A73;
  position: relative;
  min-height: 88rpx;
  box-sizing: border-box;
}

.tab-item.active {
  color: #E1251B;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 56rpx;
  height: 6rpx;
  background: #E1251B;
  border-radius: 3rpx;
}

.tab-count {
  font-size: 20rpx;
  background: #D93026;
  color: #FFFFFF;
  border-radius: 16rpx;
  padding: 2rpx 10rpx;
  margin-left: 8rpx;
  min-width: 28rpx;
  display: inline-block;
  line-height: 1.4;
}

.order-scroll {
  height: calc(100vh - 100rpx);
}

.order-card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  transition: transform 120ms ease-out, box-shadow 120ms ease-out;
}

.order-card:active {
  transform: scale(0.99);
  box-shadow: 0 2rpx 8rpx rgba(16, 24, 40, 0.06);
}

.card {
  background: #FFFFFF;
  border-radius: 24rpx;
  border: 1rpx solid #E3E5E8;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #EDEEF0;
}

.order-no {
  font-size: 24rpx;
  color: #666A73;
}

.order-status {
  font-size: 24rpx;
  font-weight: 500;
  padding: 4rpx 16rpx;
  border-radius: 12rpx;
  line-height: 1.4;
}

.tag-warning { background: #FEF3C7; color: #92400E; }
.tag-primary { background: #F5C4C0; color: #1A1A1A; }
.tag-success { background: #E8F6EF; color: #00A66A; }
.tag-info { background: #F3F4F6; color: #666A73; }
.tag-danger { background: #FDEEEC; color: #991B1B; }

.order-product {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
}

.product-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  background: #F4F5F7;
  flex-shrink: 0;
}

.product-info {
  flex: 1;
  margin: 0 16rpx;
  overflow: hidden;
}

.product-name {
  font-size: 26rpx;
  color: #1A1A1A;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-spec {
  font-size: 22rpx;
  color: #666A73;
  margin-top: 4rpx;
}

.product-qty {
  font-size: 24rpx;
  color: #666A73;
  flex-shrink: 0;
}

.order-footer {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #EDEEF0;
}

.order-time {
  font-size: 22rpx;
  color: #9AA0A8;
}

.order-amount {
  font-size: 26rpx;
  color: #1A1A1A;
}

.amount-highlight {
  color: #E1251B;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.order-actions {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 32rpx;
  border-radius: 36rpx;
  font-size: 24rpx;
  min-height: 64rpx;
  box-sizing: border-box;
  text-align: center;
  line-height: 1.2;
  transition: transform 120ms ease-out, opacity 120ms ease-out;
}

.action-btn:active {
  transform: scale(0.96);
  opacity: 0.9;
}

.btn-primary {
  background: #E1251B;
  color: #FFFFFF;
  border: 1rpx solid #E1251B;
}

.btn-ghost {
  background: transparent;
  color: #E1251B;
  border: 1rpx solid #E1251B;
}

.btn-danger {
  color: #D93026;
  border-color: #D93026;
}

.loading, .no-more, .empty-orders {
  text-align: center;
  padding: 60rpx;
  color: #666A73;
  font-size: 26rpx;
}
</style>

/* 订单搜索条（P1）：与 tabs 同底色，输入框圆角胶囊 */
.search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: #fff;
}

.search-input {
  flex: 1;
  height: 64rpx;
  background: #f6f7f9;
  border-radius: 32rpx;
  padding: 0 28rpx;
  font-size: 26rpx;
  color: $uni-text-color;
}

.search-placeholder {
  color: $uni-text-color-placeholder;
}

.search-btn {
  flex-shrink: 0;
  padding: 0 28rpx;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 32rpx;
  background: $uni-color-primary;
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
}
