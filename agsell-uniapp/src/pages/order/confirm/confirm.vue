<template>
  <view class="confirm-page">
    <NavBar title="订单确认" @back="uni.navigateBack()" />

    <view class="content">
      <!-- 收货地址 -->
      <view class="address-section card" @click="showAddressPicker = true">
        <view v-if="selectedAddress" class="address-info">
          <view class="address-main">
            <text class="receiver">{{ selectedAddress.receiver }}</text>
            <text class="phone">{{ selectedAddress.phone }}</text>
            <text v-if="selectedAddress.tag" class="tag tag-accent">{{ selectedAddress.tag }}</text>
            <text v-if="selectedAddress.isDefault === 1" class="tag tag-success">默认</text>
          </view>
          <view class="address-detail">
            {{ selectedAddress.province }} {{ selectedAddress.city }} {{ selectedAddress.district }} {{ selectedAddress.detail }}
          </view>
        </view>
        <view v-else class="address-empty" @click.stop="showAddressPicker = true">
          <text>请选择收货地址</text>
          <text class="add-btn-text">+ 新增地址</text>
        </view>
        <view class="arrow-icon"></view>
      </view>

      <!-- 商品清单 -->
      <view class="product-section card">
        <view class="section-title">商品清单</view>
        <view v-for="item in orderItems" :key="item.id" class="order-item">
          <image class="item-img" :src="item.productImage || '/static/default-product.png'" mode="aspectFill" lazy-load :alt="item.productName" />
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

      <!-- 优惠券 -->
      <view class="coupon-section card" @click="openCouponPicker">
        <text class="section-title">优惠券</text>
        <view class="coupon-row">
          <text v-if="selectedCoupon" class="coupon-selected">-¥{{ selectedCoupon.amount }}</text>
          <text v-else class="coupon-empty">暂无可用优惠券</text>
          <view class="arrow-icon"></view>
        </view>
      </view>

      <!-- 金额明细 -->
      <view class="price-section card">
        <view class="price-row">
          <text class="price-label">商品金额</text>
          <text class="price-value">¥{{ totalAmount }}</text>
        </view>
        <view class="price-row">
          <text class="price-label">运费</text>
          <text class="price-value">¥{{ freight }}</text>
        </view>
        <view v-if="discount > 0" class="price-row">
          <text class="price-label">{{ selectedCoupon?.couponName || '优惠' }}</text>
          <text class="price-value discount">-¥{{ discount }}</text>
        </view>
        <view class="divider"></view>
        <view class="price-row total">
          <text class="price-label">实付金额</text>
          <text class="price-value pay">¥{{ payAmount }}</text>
        </view>
      </view>

      <!-- 备注 -->
      <view class="remark-section card">
        <text class="section-title">备注</text>
        <textarea
          class="remark-input"
          placeholder="选填，如有备注请在此输入"
          placeholder-style="color:#9CA3AF"
          v-model="remark"
          maxlength="200"
          auto-height
        />
      </view>
    </view>

    <!-- 底部提交栏 -->
    <view class="bottom-bar">
      <view class="total-pay">
        <text class="pay-label">合计：</text>
        <text class="pay-price">¥{{ payAmount }}</text>
      </view>
      <view class="submit-btn" @click="onSubmit" role="button">提交订单</view>
    </view>

    <!-- 地址选择弹窗 -->
    <AddressPicker
      :visible="showAddressPicker"
      :addresses="addresses"
      :selected-id="selectedAddress?.id"
      @close="showAddressPicker = false"
      @add="onAddAddress"
      @select="onSelectAddress"
    />

    <!-- 优惠券选择弹层 -->
    <view v-if="showCouponPicker" class="coupon-mask" @click="showCouponPicker = false">
      <view class="coupon-panel" @click.stop>
        <view class="coupon-panel-header">
          <text class="coupon-panel-title">选择优惠券</text>
          <text class="coupon-panel-close" @click="showCouponPicker = false">关闭</text>
        </view>
        <scroll-view scroll-y class="coupon-panel-body">
          <view v-if="!couponList.usable.length && !couponList.unusable.length" class="coupon-panel-empty">
            暂无可用优惠券，去领券中心看看吧
          </view>
          <template v-if="couponList.usable.length">
            <view class="coupon-group-title">可用优惠券</view>
            <view
              v-for="c in couponList.usable"
              :key="c.id"
              class="coupon-item usable"
              :class="{ active: selectedCoupon && selectedCoupon.id === c.id }"
              @click="selectCoupon(c)"
            >
              <view class="coupon-item-left">
                <text class="coupon-item-amount">{{ c.couponType === 2 ? (Number(c.discount) * 10).toFixed(1) + '折' : '¥' + c.amount }}</text>
                <text class="coupon-item-name">{{ c.couponName }}</text>
              </view>
              <view class="coupon-item-right">
                <text v-if="Number(c.threshold) > 0" class="coupon-item-threshold">满{{ Number(c.threshold) }}可用</text>
                <text v-else class="coupon-item-threshold">无门槛</text>
                <text class="coupon-item-expire">{{ formatExpire(c.expireTime) }}</text>
                <text v-if="selectedCoupon && selectedCoupon.id === c.id" class="coupon-checked">✓ 已选</text>
              </view>
            </view>
          </template>
          <template v-if="couponList.unusable.length">
            <view class="coupon-group-title disabled-title">不可用优惠券</view>
            <view v-for="c in couponList.unusable" :key="c.id" class="coupon-item unusable">
              <view class="coupon-item-left">
                <text class="coupon-item-amount">{{ c.couponType === 2 ? (Number(c.discount) * 10).toFixed(1) + '折' : '¥' + c.amount }}</text>
                <text class="coupon-item-name">{{ c.couponName }}</text>
              </view>
              <view class="coupon-item-right">
                <text class="coupon-item-reason">{{ c.reason || '不可用' }}</text>
              </view>
            </view>
          </template>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../../components/NavBar/NavBar.vue'
import AddressPicker from '../../../components/AddressPicker/AddressPicker.vue'
import { getAddressList } from '../../../api/user'
import { createOrder } from '../../../api/order'
import { getAvailableCoupons } from '../../../api/coupon'
import { getProductDetail } from '../../../api/product'
import { request } from '../../../utils/request'

const addresses = ref([])
const selectedAddress = ref(null)
const remark = ref('')
const showAddressPicker = ref(false)
const cartData = ref([])
const orderItems = ref([])
const totalAmount = ref('0.00')
const freight = ref('0.00')
const discount = ref('0.00')
const payAmount = ref('0.00')
const buyNowProductId = ref(null)
const buyNowSpecId = ref(null)
const buyNowQuantity = ref(1)

// 优惠券
const couponList = ref({ usable: [], unusable: [] })
const selectedCoupon = ref(null)
const showCouponPicker = ref(false)

onLoad(async (options) => {
  if (options.cartData) {
    cartData.value = JSON.parse(decodeURIComponent(options.cartData))
    orderItems.value = cartData.value
    totalAmount.value = cartData.value.reduce((sum, i) => sum + (Number(i.subtotal) || 0), 0).toFixed(2)
  }
  if (options.productId) {
    buyNowProductId.value = options.productId
    buyNowSpecId.value = (options.specId && options.specId !== 'null' && options.specId !== 'undefined') ? options.specId : null
    buyNowQuantity.value = Number(options.quantity) || 1
    await loadBuyNowProduct()
  }
  await loadFreight()
  await loadAddresses()
  await loadCoupons()
})

/** 调用后端运费预估接口展示运费与实付金额（与下单计算口径一致）；接口失败时按 0 运费兜底，不阻塞下单 */
async function loadFreight() {
  const amount = Number(totalAmount.value) || 0
  freight.value = '0.00'
  recalcPay()
  try {
    const res = await request('GET', '/order/freight-preview', null, { params: { totalAmount: amount } })
    if (res.code === 0 && res.data) {
      freight.value = Number(res.data.freight || 0).toFixed(2)
      recalcPay()
    }
  } catch (e) {
    console.warn('运费预估失败，按 0 运费展示', e)
  }
}

/** 加载结算页可用券（按门槛+品类范围过滤）；已选券失效时自动清除选择 */
async function loadCoupons() {
  const amount = Number(totalAmount.value) || 0
  const pids = orderItems.value.map(i => i.productId).filter(Boolean)
  try {
    const res = await getAvailableCoupons(amount, pids)
    if (res.code === 0) {
      couponList.value = res.data || { usable: [], unusable: [] }
      const stillUsable = selectedCoupon.value
        && (couponList.value.usable || []).some(c => c.id === selectedCoupon.value.id)
      if (selectedCoupon.value && !stillUsable) {
        selectedCoupon.value = null
      }
      recalcPay()
    }
  } catch (e) {
    console.warn('可用券加载失败', e)
  }
}

function openCouponPicker() {
  if (!couponList.value.usable.length && !couponList.value.unusable.length) {
    uni.showToast({ title: '暂无可用优惠券', icon: 'none' })
    return
  }
  showCouponPicker.value = true
}

function selectCoupon(c) {
  selectedCoupon.value = selectedCoupon.value && selectedCoupon.value.id === c.id ? null : c
  recalcPay()
  showCouponPicker.value = false
}

/** 实付金额 = 商品金额 + 运费 - 优惠（金额计算最终以下单接口返回为准） */
function recalcPay() {
  const base = Number(totalAmount.value) || 0
  const freightNum = Number(freight.value) || 0
  const discountNum = selectedCoupon.value ? Number(selectedCoupon.value.amount) || 0 : 0
  discount.value = Math.min(discountNum, base).toFixed(2)
  payAmount.value = Math.max(0, base + freightNum - Number(discount.value)).toFixed(2)
}

function formatExpire(t) {
  if (!t) return ''
  const d = new Date(t.replace('T', ' ').replace(/-/g, '/'))
  const pad = n => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} 到期`
}

async function loadAddresses() {
  const res = await getAddressList()
  if (res.code === 0) {
    addresses.value = res.data || []
    const def = addresses.value.find(a => a.isDefault === 1)
    selectedAddress.value = def || addresses.value[0] || null
  }
}

async function loadBuyNowProduct() {
  const res = await getProductDetail(buyNowProductId.value)
  if (res.code === 0) {
    const p = res.data
    const price = buyNowSpecId.value
      ? (p.specs || []).find(s => s.id === buyNowSpecId.value)?.price || p.price
      : p.price
    orderItems.value = [{
      productId: p.id,
      specId: buyNowSpecId.value,
      productName: p.name,
      productImage: p.mainImage || '',
      specName: buyNowSpecId.value ? (p.specs || []).find(s => s.id === buyNowSpecId.value)?.specName || '' : '',
      price: price,
      quantity: buyNowQuantity.value,
      subtotal: (price * buyNowQuantity.value).toFixed(2)
    }]
    totalAmount.value = orderItems.value[0].subtotal
  }
}

function onAddAddress() {
  showAddressPicker.value = false
  uni.navigateTo({
    url: '/pages/mine/address?editId=',
    success: () => {}
  })
}

function onSelectAddress(addr) {
  selectedAddress.value = addr
  showAddressPicker.value = false
}

async function onSubmit() {
  if (!selectedAddress.value) {
    uni.showToast({ title: '请选择收货地址', icon: 'none' })
    return
  }

  let res
  const common = {
    addressId: selectedAddress.value.id,
    remark: remark.value,
    couponId: selectedCoupon.value ? selectedCoupon.value.id : null
  }
  if (buyNowProductId.value) {
    res = await createOrder({
      ...common,
      orderItems: orderItems.value
    })
  } else if (cartData.value.length > 0) {
    const cartItemIds = cartData.value.map(item => item.id)
    res = await createOrder({
      ...common,
      cartItemIds: cartItemIds
    })
  } else {
    uni.showToast({ title: '购物车数据异常', icon: 'none' })
    return
  }
  if (res.code === 0) {
    const { orderNo, payAmount } = res.data
    uni.redirectTo({
      url: `/pages/order/pay/pay?orderNo=${orderNo}&payAmount=${payAmount}`
    })
  } else {
    uni.showToast({ title: res.message || '下单失败', icon: 'none' })
  }
}
</script>

<style scoped>
.confirm-page {
  min-height: 100vh;
  background: #F5F5F5;
  padding-bottom: 140rpx;
}

.content {
  padding-bottom: 24rpx;
}

.card {
  background: #FFFFFF;
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1rpx solid #E5E7EB;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.address-section {
  display: flex;
  align-items: center;
  position: relative;
}

.address-info {
  flex: 1;
}

.address-main {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex-wrap: wrap;
}

.receiver {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.phone {
  font-size: 26rpx;
  color: #4B5563;
}

.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 12rpx;
  line-height: 1.4;
}

.tag-accent { background: #FFF7ED; color: #9A3412; }
.tag-success { background: #D1FAE5; color: #00B578; }

.address-detail {
  font-size: 26rpx;
  color: #4B5563;
  margin-top: 8rpx;
  line-height: 1.5;
}

.address-empty {
  flex: 1;
  color: #4B5563;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.add-btn-text {
  color: #00B578;
  font-weight: 500;
}

.arrow-icon {
  width: 18rpx;
  height: 18rpx;
  border-top: 4rpx solid #9CA3AF;
  border-right: 4rpx solid #9CA3AF;
  border-radius: 2rpx;
  transform: rotate(45deg);
  flex-shrink: 0;
  margin-left: 16rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #00B578;
  margin-bottom: 20rpx;
  display: block;
}

.order-item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #F0F0F0;
}

.order-item:last-child { border-bottom: none; }

.item-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  background: #F5F5F5;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  margin-left: 16rpx;
  overflow: hidden;
}

.item-name {
  font-size: 26rpx;
  color: #111827;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-spec {
  font-size: 22rpx;
  color: #4B5563;
  margin-top: 4rpx;
}

.item-bottom {
  display: flex;
  align-items: baseline;
  margin-top: 8rpx;
}

.item-price {
  font-size: 26rpx;
  color: #E63946;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.item-qty {
  font-size: 24rpx;
  color: #4B5563;
  margin-left: 8rpx;
}

.item-subtotal {
  font-size: 28rpx;
  color: #111827;
  font-weight: 600;
  margin-left: 16rpx;
  font-variant-numeric: tabular-nums;
}

.price-row {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
  font-size: 28rpx;
}

.price-label { color: #4B5563; }
.price-value { color: #111827; font-variant-numeric: tabular-nums; }

.price-row.total {
  font-size: 32rpx;
  font-weight: 600;
}

.price-value.pay { color: #E63946; }

.divider {
  height: 1rpx;
  background: #F0F0F0;
  margin: 8rpx 0;
}

.remark-input {
  width: 100%;
  min-height: 88rpx;
  max-height: 280rpx;
  font-size: 28rpx;
  line-height: 1.5;
  color: #111827;
  background: #F5F5F5;
  border-radius: 12rpx;
  padding: 16rpx 24rpx;
  box-sizing: border-box;
  border: 1rpx solid #D1D5DB;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 100rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #E5E7EB;
  z-index: 100;
}

.total-pay {
  flex: 1;
}

.pay-label { font-size: 26rpx; color: #4B5563; }

.pay-price {
  font-size: 40rpx;
  color: #E63946;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.submit-btn {
  background: #00B578;
  color: #FFFFFF;
  padding: 0 60rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
  min-width: 88rpx;
  text-align: center;
}

/* 优惠券选择行 */
.coupon-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.coupon-section .section-title {
  margin-bottom: 0;
}

.coupon-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.coupon-selected {
  color: #B45309;
  font-size: 30rpx;
  font-weight: 600;
}

.coupon-empty {
  color: #9CA3AF;
  font-size: 26rpx;
}

.price-value.discount {
  color: #B45309;
  font-weight: 600;
}

/* 优惠券选择弹层 */
.coupon-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 200;
  display: flex;
  align-items: flex-end;
}

.coupon-panel {
  width: 100%;
  background: #F5F5F5;
  border-radius: 32rpx 32rpx 0 0;
  padding: 32rpx 24rpx;
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  max-height: 65vh;
  display: flex;
  flex-direction: column;
}

.coupon-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.coupon-panel-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
}

.coupon-panel-close {
  font-size: 26rpx;
  color: #6B7280;
  padding: 8rpx 16rpx;
}

.coupon-panel-body {
  flex: 1;
  max-height: 50vh;
}

.coupon-panel-empty {
  text-align: center;
  color: #9CA3AF;
  font-size: 26rpx;
  padding: 80rpx 0;
}

.coupon-group-title {
  font-size: 24rpx;
  color: #00B578;
  font-weight: 600;
  margin: 16rpx 0 12rpx;
}

.disabled-title {
  color: #9CA3AF;
}

.coupon-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 16rpx;
  border: 1rpx solid #E5E7EB;
}

.coupon-item.usable:active {
  background: #F0FDF4;
}

.coupon-item.usable.active {
  border-color: #00B578;
  background: #F0FDF4;
}

.coupon-item.unusable {
  opacity: 0.55;
}

.coupon-item-left {
  display: flex;
  flex-direction: column;
}

.coupon-item-amount {
  font-size: 36rpx;
  font-weight: 700;
  color: #B45309;
}

.coupon-item-name {
  font-size: 24rpx;
  color: #374151;
  margin-top: 4rpx;
}

.coupon-item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.coupon-item-threshold {
  font-size: 22rpx;
  color: #4B5563;
}

.coupon-item-expire {
  font-size: 22rpx;
  color: #9CA3AF;
  margin-top: 4rpx;
}

.coupon-item-reason {
  font-size: 22rpx;
  color: #9CA3AF;
}

.coupon-checked {
  font-size: 22rpx;
  color: #00B578;
  font-weight: 600;
  margin-top: 4rpx;
}
</style>
