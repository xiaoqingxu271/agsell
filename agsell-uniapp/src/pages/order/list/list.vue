<template>
  <view class="order-list-page">
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
          <image class="product-img" :src="item.productImage || '/static/default-product.png'" mode="aspectFill" :alt="item.productName" />
          <view class="product-info">
            <text class="product-name">{{ item.productName }}</text>
            <text v-if="item.specName" class="product-spec">{{ item.specName }}</text>
          </view>
          <text class="product-qty">×{{ item.quantity }}</text>
        </view>

        <view class="order-footer">
          <text class="order-time">{{ order.createTime }}</text>
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
import { createPayment } from '../../../api/payment'

const tabs = [
  { label: '全部', value: null, count: 0 },
  { label: '待付款', value: 0, count: 0 },
  { label: '待发货', value: 1, count: 0 },
  { label: '待收货', value: 2, count: 0 },
  { label: '已完成', value: 3, count: 0 }
]
const currentTab = ref(0)
const orders = ref([])
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

async function loadOrders() {
  if (loading.value) return
  loading.value = true
  const currentTabValue = tabs[currentTab.value].value
  const res = await getOrderList({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    status: currentTabValue
  })
  if (res.code === 0) {
    const { records, current, pages, total } = res.data
    orders.value = pageNum.value === 1 ? records : [...orders.value, ...records]
    hasMore.value = current < pages
    // 记录当前状态的订单总数，用于"我的"页面未读红点计算
    if (currentTabValue !== null && currentTabValue !== undefined) {
      uni.setStorageSync('order_last_total_' + currentTabValue, total || 0)
    }
  }
  loading.value = false
  refreshing.value = false
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

async function onPayOrder(order) {
  await createPayment(order.orderNo)
  uni.showToast({ title: '支付成功', icon: 'success' })
  loadOrders()
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
  background: #F0FDF4;
}

.tabs {
  display: flex;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E7EB;
  position: sticky;
  top: 0;
  z-index: 10;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 28rpx 0;
  font-size: 28rpx;
  color: #6B7280;
  position: relative;
  min-height: 88rpx;
  box-sizing: border-box;
}

.tab-item.active {
  color: #15803D;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 6rpx;
  background: #15803D;
  border-radius: 3rpx;
}

.tab-count {
  font-size: 20rpx;
  background: #DC2626;
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
}

.card {
  background: #FFFFFF;
  border-radius: 24rpx;
  border: 1px solid #BBF7D0;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 1px solid #E5E7EB;
}

.order-no {
  font-size: 24rpx;
  color: #6B7280;
}

.order-status {
  font-size: 24rpx;
  font-weight: 500;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  line-height: 1.4;
}

.tag-warning { background: #FEF3C7; color: #92400E; }
.tag-primary { background: #BBF7D0; color: #14532D; }
.tag-success { background: #DCFCE7; color: #166534; }
.tag-info { background: #F3F4F6; color: #4B5563; }
.tag-danger { background: #FEE2E2; color: #991B1B; }

.order-product {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
}

.product-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  background: #F0FDF4;
  flex-shrink: 0;
}

.product-info {
  flex: 1;
  margin: 0 16rpx;
  overflow: hidden;
}

.product-name {
  font-size: 26rpx;
  color: #1F2937;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-spec {
  font-size: 22rpx;
  color: #6B7280;
  margin-top: 4rpx;
}

.product-qty {
  font-size: 24rpx;
  color: #6B7280;
  flex-shrink: 0;
}

.order-footer {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
  padding-top: 16rpx;
  border-top: 1px solid #E5E7EB;
}

.order-time {
  font-size: 22rpx;
  color: #9CA3AF;
}

.order-amount {
  font-size: 26rpx;
  color: #1F2937;
}

.amount-highlight {
  color: #A16207;
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
  border-radius: 40rpx;
  font-size: 24rpx;
  min-height: 56rpx;
  box-sizing: border-box;
  text-align: center;
  line-height: 1.2;
}

.btn-primary {
  background: #15803D;
  color: #FFFFFF;
  border: 1px solid #15803D;
}

.btn-ghost {
  background: transparent;
  color: #15803D;
  border: 1px solid #15803D;
}

.btn-danger {
  color: #DC2626;
  border-color: #DC2626;
}

.loading, .no-more, .empty-orders {
  text-align: center;
  padding: 60rpx;
  color: #9CA3AF;
  font-size: 26rpx;
}
</style>
