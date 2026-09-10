<template>
  <view class="order-detail-page">
    <NavBar title="订单详情" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="content">
      <!-- 状态卡片 -->
      <view class="status-card">
        <view class="status-badge">
          <view class="status-check"></view>
        </view>
        <view class="status-info">
          <text class="status-text">{{ orderDetail?.statusText }}</text>
          <text class="status-hint">{{ getStatusHint(orderDetail?.status) }}</text>
        </view>
      </view>

      <!-- 订单信息 -->
      <view class="section-card">
        <view class="card-title">订单信息</view>
        <view class="info-row">
          <text class="info-label">订单编号</text>
          <text class="info-value order-no">{{ orderDetail?.orderNo }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">下单时间</text>
          <text class="info-value">{{ formatTime(orderDetail?.createTime) }}</text>
        </view>
        <view v-if="orderDetail?.payTime" class="info-row">
          <text class="info-label">支付时间</text>
          <text class="info-value">{{ formatTime(orderDetail?.payTime) }}</text>
        </view>
        <view v-if="orderDetail?.deliveryTime" class="info-row">
          <text class="info-label">发货时间</text>
          <text class="info-value">{{ formatTime(orderDetail?.deliveryTime) }}</text>
        </view>
        <view v-if="orderDetail?.receiveTime" class="info-row">
          <text class="info-label">收货时间</text>
          <text class="info-value">{{ formatTime(orderDetail?.receiveTime) }}</text>
        </view>
      </view>

      <!-- 商品清单 -->
      <view class="section-card">
        <view class="card-title">商品清单</view>
        <view v-for="item in orderDetail?.items" :key="item.productId" class="goods-item">
          <image class="goods-img" :src="item.productImage || '/static/default-product.png'" mode="aspectFill" lazy-load :alt="item.productName" />
          <view class="goods-info">
            <text class="goods-name">{{ item.productName }}</text>
            <text v-if="item.specName" class="goods-spec">{{ item.specName }}</text>
            <view class="goods-bottom">
              <text class="goods-price">¥{{ item.price }}</text>
              <text class="goods-qty">×{{ item.quantity }}</text>
            </view>
          </view>
          <text class="goods-subtotal">¥{{ item.subtotal }}</text>
        </view>
      </view>

      <!-- 金额明细 -->
      <view class="section-card">
        <view class="amount-row">
          <text class="amount-label">商品金额</text>
          <text class="amount-value">¥{{ orderDetail?.totalAmount }}</text>
        </view>
        <view class="amount-row">
          <text class="amount-label">运费</text>
          <text class="amount-value">¥{{ orderDetail?.freight || '0.00' }}</text>
        </view>
        <view v-if="Number(orderDetail?.discount) > 0" class="amount-row">
          <text class="amount-label">优惠</text>
          <text class="amount-value discount">-¥{{ orderDetail?.discount }}</text>
        </view>
        <view class="amount-divider"></view>
        <view class="amount-row total">
          <text class="amount-label">实付金额</text>
          <text class="amount-value total-value">¥{{ orderDetail?.payAmount }}</text>
        </view>
      </view>

      <!-- 收货信息 -->
      <view v-if="orderDetail" class="section-card">
        <view class="card-title">收货信息</view>
        <view class="address-main">
          <text class="receiver">{{ orderDetail.receiver }}</text>
          <text class="phone">{{ orderDetail.phone }}</text>
        </view>
        <text class="address-detail">{{ orderDetail.address }}</text>
      </view>

      <!-- 物流信息 -->
      <view v-if="orderDetail?.logType" class="section-card">
        <view class="card-title">物流信息</view>
        <view class="logistics-row">
          <text class="logistics-company">{{ orderDetail.logType }}</text>
          <text class="logistics-no">{{ orderDetail.logNo }}</text>
        </view>
      </view>

      <!-- 底部安全占位：防止底部操作栏遮挡最后内容 -->
      <view class="bottom-space"></view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view v-if="orderDetail" class="bottom-bar">
      <view
        v-if="orderDetail.status === 0"
        class="action-btn btn-ghost btn-danger"
        @click="onCancel"
      >取消订单</view>
      <view
        v-if="orderDetail.status === 0"
        class="action-btn btn-primary"
        @click="onPay"
      >去支付</view>
      <view
        v-if="orderDetail.status === 2"
        class="action-btn btn-primary"
        @click="onConfirmReceive"
      >确认收货</view>
      <view
        v-if="orderDetail.status === 3"
        class="action-btn btn-primary"
        @click="onReview"
      >评价</view>
      <view
        v-if="orderDetail.status === 3"
        class="action-btn btn-ghost"
        @click="onRepurchase"
      >再次购买</view>
      <view
        v-if="orderDetail.status === 2 || orderDetail.status === 3"
        class="action-btn btn-ghost btn-warning"
        @click="onApplyAfterSales"
      >申请售后</view>
      <view
        v-if="orderDetail.status === 5"
        class="action-btn btn-ghost"
        @click="onViewAfterSales"
      >查看售后</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../../components/NavBar/NavBar.vue'
import { getOrderDetail, cancelOrder, confirmReceive } from '../../../api/order'
import { createPayment } from '../../../api/payment'
import { getAfterSalesList } from '../../../api/afterSales'

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

function formatTime(time) {
  if (!time) return ''
  return String(time).replace('T', ' ')
}

function getStatusHint(status) {
  const hints = {
    0: '请尽快完成支付',
    1: '商家正在准备发货',
    2: '商品运输中，请注意查收',
    3: '感谢您的购买',
    4: '订单已关闭，如有疑问请联系客服',
    5: '退款申请已提交，等待商家处理',
    6: '退款已到账，交易完成'
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
  uni.navigateTo({
    url: `/pages/review/write/write?orderNo=${orderDetail.value.orderNo}`
  })
}

function onRepurchase() {
  const items = orderDetail.value?.items || []
  if (items.length === 0) return
  uni.navigateTo({ url: `/pages/product/product?id=${items[0].productId}` })
}

function onApplyAfterSales() {
  uni.navigateTo({ url: `/pages/after-sales/apply/apply?orderNo=${orderDetail.value.orderNo}` })
}

async function onViewAfterSales() {
  // 优先跳到该订单的售后单详情，找不到则回落到售后列表
  const res = await getAfterSalesList({ pageNum: 1, pageSize: 10 })
  if (res.code === 0) {
    const found = (res.data?.records || []).find(i => i.orderNo === orderDetail.value.orderNo)
    if (found) {
      uni.navigateTo({ url: `/pages/after-sales/detail/detail?afterSalesNo=${found.afterSalesNo}` })
      return
    }
  }
  uni.navigateTo({ url: '/pages/after-sales/list/list' })
}
</script>

<style scoped>
.order-detail-page {
  min-height: 100vh;
  background: #F4F6F5;
  display: flex;
  flex-direction: column;
}

.content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

/* ===== 状态卡片（深翡翠渐变，signature） ===== */
.status-card {
  display: flex;
  align-items: center;
  margin: 24rpx 24rpx 8rpx;
  padding: 40rpx 32rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #F0FDF4 0%, #DCFCE7 100%);
  border: 1rpx solid #DCFCE7;
  box-shadow: 0 8rpx 24rpx rgba(16, 24, 40, 0.06);
}

.status-badge {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #FFFFFF;
  border: 1rpx solid #BBF7D0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 28rpx;
}

.status-check {
  width: 34rpx;
  height: 18rpx;
  border-left: 6rpx solid #15803D;
  border-bottom: 6rpx solid #15803D;
  border-radius: 2rpx;
  transform: rotate(-45deg);
  margin-top: -4rpx;
}

.status-info {
  flex: 1;
  min-width: 0;
}

.status-text {
  font-size: 38rpx;
  font-weight: 700;
  color: #14532D;
  display: block;
  margin-bottom: 6rpx;
}

.status-hint {
  font-size: 26rpx;
  color: #4B5563;
  display: block;
}

/* ===== 通用卡片 ===== */
.section-card {
  background: #FFFFFF;
  margin: 16rpx 24rpx;
  padding: 28rpx;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #15803D;
  margin-bottom: 20rpx;
  padding-left: 18rpx;
  position: relative;
}

.card-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6rpx;
  bottom: 6rpx;
  width: 8rpx;
  border-radius: 4rpx;
  background: linear-gradient(180deg, #22C55E, #15803D);
}

/* ===== 订单信息 ===== */
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #EEF1EF;
  font-size: 26rpx;
}

.info-row:last-child { border-bottom: none; }

.info-label {
  color: #6B7280;
  flex-shrink: 0;
  margin-right: 24rpx;
}

.info-value {
  color: #1F2937;
  text-align: right;
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}

.order-no {
  font-size: 24rpx;
  color: #4B5563;
}

/* ===== 商品清单 ===== */
.goods-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #EEF1EF;
}

.goods-item:last-child { border-bottom: none; }

.goods-img {
  width: 128rpx;
  height: 128rpx;
  border-radius: 16rpx;
  background: #F4F6F5;
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  margin: 0 20rpx;
  min-width: 0;
  overflow: hidden;
}

.goods-name {
  font-size: 28rpx;
  color: #1F2937;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.goods-spec {
  font-size: 22rpx;
  color: #9CA3AF;
  margin-top: 6rpx;
  display: block;
}
.goods-bottom {
  display: flex;
  align-items: baseline;
  margin-top: 10rpx;
}

.goods-price {
  font-size: 28rpx;
  color: #A16207;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.goods-qty {
  font-size: 24rpx;
  color: #9CA3AF;
  margin-left: 10rpx;
}

.goods-subtotal {
  font-size: 28rpx;
  color: #1F2937;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

/* ===== 金额明细 ===== */
.amount-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
  font-size: 28rpx;
}

.amount-label { color: #6B7280; }

.amount-value {
  color: #1F2937;
  font-variant-numeric: tabular-nums;
}

.amount-value.discount { color: #DC2626; }

.amount-divider {
  height: 1px;
  background: #EEF1EF;
  margin: 8rpx 0;
}

.amount-row.total {
  padding-top: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
}

.amount-value.total-value {
  color: #A16207;
  font-size: 36rpx;
  font-weight: 700;
}

/* ===== 收货信息 ===== */
.address-main {
  display: flex;
  align-items: center;
  margin-bottom: 12rpx;
}

.receiver {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}

.phone {
  font-size: 26rpx;
  color: #6B7280;
  margin-left: 24rpx;
  font-variant-numeric: tabular-nums;
}

.address-detail {
  font-size: 26rpx;
  color: #6B7280;
  line-height: 1.6;
}

/* ===== 物流信息 ===== */
.logistics-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logistics-company {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2937;
}

.logistics-no {
  font-size: 26rpx;
  color: #6B7280;
  font-variant-numeric: tabular-nums;
}

.bottom-space {
  height: 180rpx;
}

/* ===== 底部操作栏 ===== */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #E3E7E5;
  z-index: 100;
  justify-content: flex-end;
}

.action-btn {
  flex: 1;
  max-width: 240rpx;
  text-align: center;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
  font-weight: 500;
  box-sizing: border-box;
  transition: transform 120ms ease-out, opacity 120ms ease-out;
}

.action-btn:active {
  transform: scale(0.96);
  opacity: 0.9;
}

.btn-primary {
  background: #15803D;
  color: #FFFFFF;
  border: 1rpx solid #15803D;
}

.btn-ghost {
  background: transparent;
  color: #15803D;
  border: 1rpx solid #15803D;
}

.btn-danger {
  color: #DC2626;
  border-color: #DC2626;
}

.btn-warning {
  color: #D97706;
  border-color: #D97706;
}
</style>
