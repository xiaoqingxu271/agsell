<template>
  <view class="order-detail-page">
    <NavBar :title="orderDetail?.statusText || '订单详情'" />

    <scroll-view scroll-y class="content">
      <!-- 订单状态 -->
      <view class="status-section card">
        <view class="status-icon">{{ getStatusIcon(orderDetail?.status) }}</view>
        <text class="status-text">{{ orderDetail?.statusText }}</text>
        <text class="status-hint">{{ getStatusHint(orderDetail?.status) }}</text>
      </view>

      <!-- 基本信息 -->
      <view class="info-section card">
        <view class="info-row">
          <text class="info-label">订单编号</text>
          <text class="info-value">{{ orderDetail?.orderNo }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">下单时间</text>
          <text class="info-value">{{ orderDetail?.createTime }}</text>
        </view>
        <view v-if="orderDetail?.payTime" class="info-row">
          <text class="info-label">支付时间</text>
          <text class="info-value">{{ orderDetail?.payTime }}</text>
        </view>
        <view v-if="orderDetail?.deliveryTime" class="info-row">
          <text class="info-label">发货时间</text>
          <text class="info-value">{{ orderDetail?.deliveryTime }}</text>
        </view>
        <view v-if="orderDetail?.receiveTime" class="info-row">
          <text class="info-label">收货时间</text>
          <text class="info-value">{{ orderDetail?.receiveTime }}</text>
        </view>
      </view>

      <!-- 商品清单 -->
      <view class="product-section card">
        <view class="section-title">商品清单</view>
        <view v-for="item in orderDetail?.items" :key="item.productId" class="order-item">
          <image class="item-img" :src="item.productImage || '/static/default-product.png'" mode="aspectFill" />
          <view class="item-info">
            <text class="item-name">{{ item.productName }}</text>
            <text v-if="item.specName" class="item-spec">{{ item.specName }}</text>
            <view class="item-bottom">
              <text class="item-price">¥{{ item.price }}</text>
              <text class="item-qty">×{{ item.quantity }}</text>
            </view>
          </view>
          <text class="item-subtotal">¥{{ item.subtotal }}</text>
        </view>
      </view>

      <!-- 金额明细 -->
      <view class="price-section card">
        <view class="price-row">
          <text class="price-label">商品金额</text>
          <text class="price-value">¥{{ orderDetail?.totalAmount }}</text>
        </view>
        <view class="price-row">
          <text class="price-label">运费</text>
          <text class="price-value">¥{{ orderDetail?.freight || '0.00' }}</text>
        </view>
        <view class="price-row">
          <text class="price-label">优惠</text>
          <text class="price-value">-¥{{ orderDetail?.discount || '0.00' }}</text>
        </view>
        <view class="divider"></view>
        <view class="price-row total">
          <text class="price-label">实付金额</text>
          <text class="price-value pay">¥{{ orderDetail?.payAmount }}</text>
        </view>
      </view>

      <!-- 收货地址 -->
      <view v-if="orderDetail" class="address-section card">
        <view class="section-title">收货地址</view>
        <view class="address-info">
          <view class="address-main">
            <text class="receiver">{{ orderDetail.receiver }}</text>
            <text class="phone">{{ orderDetail.phone }}</text>
          </view>
          <text class="address-detail">{{ orderDetail.address }}</text>
        </view>
      </view>

      <!-- 物流信息 -->
      <view v-if="orderDetail?.logType" class="logistics-section card">
        <view class="section-title">物流信息</view>
        <view class="logistics-info">
          <text class="logistics-company">{{ orderDetail.logType }}</text>
          <text class="logistics-no">{{ orderDetail.logNo }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view v-if="orderDetail" class="bottom-bar">
      <view
        v-if="orderDetail.status === 0"
        class="action-btn cancel"
        @click="onCancel"
      >取消订单</view>
      <view
        v-if="orderDetail.status === 0"
        class="action-btn pay"
        @click="onPay"
      >去支付</view>
      <view
        v-if="orderDetail.status === 2"
        class="action-btn confirm"
        @click="onConfirmReceive"
      >确认收货</view>
      <view
        v-if="orderDetail.status === 3"
        class="action-btn review"
        @click="onReview"
      >评价</view>
      <view
        v-if="orderDetail.status === 3"
        class="action-btn repurchase"
        @click="onRepurchase"
      >再次购买</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../../components/NavBar/NavBar.vue'
import { getOrderDetail, cancelOrder, confirmReceive } from '../../../api/order'
import { createPayment } from '../../../api/payment'

const orderDetail = ref(null)

onLoad(async (options) => {
  const orderNo = options.orderNo
  if (orderNo) await loadDetail(orderNo)
})

async function loadDetail(orderNo) {
  const res = await getOrderDetail(orderNo)
  if (res.code === 0) {
    orderDetail.value = res.data
  }
}

function getStatusIcon(status) {
  const icons = { 0: '💳', 1: '📦', 2: '🚚', 3: '✅', 4: '❌', 5: '🔄' }
  return icons[status] || '📋'
}

function getStatusHint(status) {
  const hints = {
    0: '请尽快完成支付',
    1: '商家正在准备发货',
    2: '商品运输中，请注意查收',
    3: '感谢您的购买',
    4: '订单已取消',
    5: '售后处理中'
  }
  return hints[status] || ''
}

async function onCancel() {
  uni.showModal({
    title: '取消订单',
    content: '确定要取消该订单吗？',
    success: async (res) => {
      if (res.confirm) {
        await cancelOrder(orderDetail.value.orderNo, '用户主动取消')
        uni.showToast({ title: '订单已取消', icon: 'success' })
        loadDetail(orderDetail.value.orderNo)
      }
    }
  })
}

async function onPay() {
  await createPayment(orderDetail.value.orderNo)
  uni.showToast({ title: '支付成功', icon: 'success' })
  loadDetail(orderDetail.value.orderNo)
}

async function onConfirmReceive() {
  uni.showModal({
    title: '确认收货',
    content: '确认已收到商品？',
    success: async (res) => {
      if (res.confirm) {
        await confirmReceive(orderDetail.value.orderNo)
        uni.showToast({ title: '确认收货成功', icon: 'success' })
        loadDetail(orderDetail.value.orderNo)
      }
    }
  })
}

function onReview() {
  const items = orderDetail.value?.items || []
  if (items.length === 0) return
  const item = items[0]
  uni.navigateTo({
    url: `/pages/review/write/write?orderId=${orderDetail.value.id}&productId=${item.productId}&productName=${encodeURIComponent(item.productName)}&productImage=${encodeURIComponent(item.productImage || '')}&specName=${encodeURIComponent(item.specName || '')}`
  })
}

function onRepurchase() {
  const items = orderDetail.value?.items || []
  if (items.length === 0) return
  uni.navigateTo({ url: `/pages/product/product?id=${items[0].productId}` })
}
</script>

<style scoped>
.order-detail-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 120rpx;
}

.content {
  height: calc(100vh - 100rpx);
  overflow-y: auto;
}

.card {
  background: #fff;
  margin: 20rpx;
  padding: 24rpx;
  border-radius: 16rpx;
}

.status-section {
  text-align: center;
  padding: 40rpx 24rpx;
}

.status-icon {
  font-size: 80rpx;
  display: block;
  margin-bottom: 16rpx;
}

.status-text {
  font-size: 36rpx;
  font-weight: bold;
  color: #333;
  display: block;
  margin-bottom: 8rpx;
}

.status-hint {
  font-size: 26rpx;
  color: #999;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
  font-size: 26rpx;
}

.info-label { color: #999; }
.info-value { color: #333; }

.section-title {
  font-size: 30rpx;
  font-weight: bold;
  color: #333;
  margin-bottom: 20rpx;
}

.order-item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.order-item:last-child { border-bottom: none; }

.item-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 8rpx;
  background: #f5f5f5;
}

.item-info {
  flex: 1;
  margin: 0 16rpx;
  overflow: hidden;
}

.item-name {
  font-size: 26rpx;
  color: #333;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-spec {
  font-size: 22rpx;
  color: #999;
  margin-top: 4rpx;
}

.item-bottom {
  display: flex;
  align-items: baseline;
  margin-top: 8rpx;
}

.item-price {
  font-size: 26rpx;
  color: #FF9800;
  font-weight: bold;
}

.item-price::before { content: '¥'; font-size: 20rpx; }

.item-qty { font-size: 24rpx; color: #999; margin-left: 8rpx; }

.item-subtotal {
  font-size: 28rpx;
  color: #333;
  font-weight: bold;
}

.price-row {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
  font-size: 28rpx;
}

.price-label { color: #666; }
.price-value { color: #333; }

.price-row.total {
  font-size: 32rpx;
  font-weight: bold;
}

.price-value.pay { color: #FF9800; }

.divider {
  height: 1rpx;
  background: #eee;
  margin: 8rpx 0;
}

.address-info {
  padding: 8rpx 0;
}

.address-main {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-bottom: 12rpx;
}

.receiver {
  font-size: 30rpx;
  font-weight: bold;
  color: #333;
}

.phone {
  font-size: 26rpx;
  color: #666;
}

.address-detail {
  font-size: 26rpx;
  color: #666;
  line-height: 1.6;
}

.logistics-info {
  display: flex;
  gap: 32rpx;
}

.logistics-company, .logistics-no {
  font-size: 26rpx;
  color: #333;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 20rpx;
  padding: 20rpx 24rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
  z-index: 100;
}

.action-btn {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  border-radius: 44rpx;
  font-size: 28rpx;
  border: 1rpx solid;
}

.action-btn.cancel { color: #F44336; border-color: #F44336; background: #fff; }
.action-btn.pay { color: #fff; border-color: #FF9800; background: #FF9800; }
.action-btn.confirm { color: #fff; border-color: #4CAF50; background: #4CAF50; }
.action-btn.review { color: #fff; border-color: #2196F3; background: #2196F3; }
.action-btn.repurchase { color: #666; border-color: #ccc; background: #fff; }
</style>
