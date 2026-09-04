<template>
  <view class="order-list-page">
    <view class="tabs">
      <view
        v-for="(tab, index) in tabs"
        :key="tab.value"
        class="tab-item"
        :class="{ active: currentTab === index }"
        @click="onTabChange(index)"
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
          <text class="order-status" :style="{ color: getStatusColor(order.status) }">
            {{ order.statusText }}
          </text>
        </view>

        <view v-for="item in order.items" :key="item.productId" class="order-product">
          <image class="product-img" :src="item.productImage || '/static/default-product.png'" mode="aspectFill" />
          <view class="product-info">
            <text class="product-name">{{ item.productName }}</text>
            <text v-if="item.specName" class="product-spec">{{ item.specName }}</text>
          </view>
          <text class="product-qty">×{{ item.quantity }}</text>
        </view>

        <view class="order-footer">
          <text class="order-time">{{ order.createTime }}</text>
          <text class="order-amount">共{{ order.itemCount }}件　合计：¥{{ order.payAmount }}</text>
          <view class="order-actions">
            <view
              v-if="order.status === 0"
              class="action-btn cancel"
              @click.stop="onCancelOrder(order)"
            >取消订单</view>
            <view
              v-if="order.status === 0"
              class="action-btn pay"
              @click.stop="onPayOrder(order)"
            >去支付</view>
            <view
              v-if="order.status === 2"
              class="action-btn confirm"
              @click.stop="onConfirmReceive(order)"
            >确认收货</view>
            <view
              v-if="order.status === 3"
              class="action-btn review"
              @click.stop="onReview(order)"
            >评价</view>
            <view
              v-if="order.status === 3"
              class="action-btn repurchase"
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
import { onShow } from '@dcloudio/uni-app'
import { onMounted } from 'vue'
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

const statusText = { 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已取消', 5: '售后中' }
const statusColor = { 0: '#FF9800', 1: '#2196F3', 2: '#9C27B0', 3: '#4CAF50', 4: '#999', 5: '#F44336' }

onShow(async () => {
  if (orders.value.length === 0) await loadOrders()
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
    const { records, current, pages } = res.data
    orders.value = pageNum.value === 1 ? records : [...orders.value, ...records]
    hasMore.value = current < pages
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

function getStatusColor(status) {
  return statusColor[status] || '#999'
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
    url: `/pages/review/write/write?orderId=${order.id}&productId=`
  })
}

function onRepurchase(order) {
  uni.navigateTo({ url: `/pages/order/detail/detail?orderNo=${order.orderNo}` })
}
</script>

<style scoped>
.order-list-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.tabs {
  display: flex;
  background: #fff;
  border-bottom: 1rpx solid #eee;
  position: sticky;
  top: 0;
  z-index: 10;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 28rpx 0;
  font-size: 28rpx;
  color: #666;
  position: relative;
}

.tab-item.active {
  color: #4CAF50;
  font-weight: bold;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 6rpx;
  background: #4CAF50;
  border-radius: 3rpx;
}

.tab-count {
  font-size: 20rpx;
  background: #FF9800;
  color: #fff;
  border-radius: 50%;
  padding: 2rpx 10rpx;
  margin-left: 8rpx;
}

.order-scroll {
  height: calc(100vh - 100rpx);
}

.order-card {
  margin: 20rpx;
  padding: 24rpx;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.order-no {
  font-size: 24rpx;
  color: #999;
}

.order-status {
  font-size: 28rpx;
  font-weight: bold;
}

.order-product {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
}

.product-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 8rpx;
  background: #f5f5f5;
}

.product-info {
  flex: 1;
  margin: 0 16rpx;
  overflow: hidden;
}

.product-name {
  font-size: 26rpx;
  color: #333;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-spec {
  font-size: 22rpx;
  color: #999;
  margin-top: 4rpx;
}

.product-qty {
  font-size: 24rpx;
  color: #666;
}

.order-footer {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f5f5;
}

.order-time {
  font-size: 22rpx;
  color: #999;
}

.order-amount {
  font-size: 26rpx;
  color: #333;
}

.order-actions {
  display: flex;
  gap: 16rpx;
}

.action-btn {
  padding: 12rpx 28rpx;
  border-radius: 32rpx;
  font-size: 24rpx;
  border: 1rpx solid;
}

.action-btn.cancel { color: #F44336; border-color: #F44336; background: #fff; }
.action-btn.pay { color: #fff; border-color: #FF9800; background: #FF9800; }
.action-btn.confirm { color: #fff; border-color: #4CAF50; background: #4CAF50; }
.action-btn.review { color: #fff; border-color: #2196F3; background: #2196F3; }
.action-btn.repurchase { color: #666; border-color: #ccc; background: #fff; }

.loading, .no-more, .empty-orders {
  text-align: center;
  padding: 60rpx;
  color: #999;
  font-size: 26rpx;
}
</style>
