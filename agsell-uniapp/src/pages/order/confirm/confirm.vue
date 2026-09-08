<template>
  <view class="confirm-page">
    <NavBar title="订单确认" />

    <scroll-view scroll-y class="content">
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
        <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="9 18 15 12 9 6"></polyline>
        </svg>
      </view>

      <!-- 商品清单 -->
      <view class="product-section card">
        <view class="section-title">商品清单</view>
        <view v-for="item in orderItems" :key="item.id" class="order-item">
          <image class="item-img" :src="item.productImage || '/static/default-product.png'" mode="aspectFill" :alt="item.productName" />
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
          <text class="price-value">¥{{ totalAmount }}</text>
        </view>
        <view class="price-row">
          <text class="price-label">运费</text>
          <text class="price-value">¥0.00</text>
        </view>
        <view class="divider"></view>
        <view class="price-row total">
          <text class="price-label">实付金额</text>
          <text class="price-value pay">¥{{ totalAmount }}</text>
        </view>
      </view>

      <!-- 备注 -->
      <view class="remark-section card">
        <text class="section-title">备注</text>
        <input
          class="remark-input"
          type="text"
          placeholder="选填，如有备注请在此输入"
          placeholder-style="color:#9CA3AF"
          v-model="remark"
          maxlength="200"
        />
      </view>
    </scroll-view>

    <!-- 底部提交栏 -->
    <view class="bottom-bar">
      <view class="total-pay">
        <text class="pay-label">合计：</text>
        <text class="pay-price">¥{{ totalAmount }}</text>
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
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../../components/NavBar/NavBar.vue'
import AddressPicker from '../../../components/AddressPicker/AddressPicker.vue'
import { getAddressList } from '../../../api/user'
import { createOrder } from '../../../api/order'
import { getProductDetail } from '../../../api/product'

const addresses = ref([])
const selectedAddress = ref(null)
const remark = ref('')
const showAddressPicker = ref(false)
const cartData = ref([])
const orderItems = ref([])
const totalAmount = ref('0.00')
const buyNowProductId = ref(null)
const buyNowSpecId = ref(null)
const buyNowQuantity = ref(1)

onLoad(async (options) => {
  if (options.cartData) {
    cartData.value = JSON.parse(decodeURIComponent(options.cartData))
    orderItems.value = cartData.value
    totalAmount.value = cartData.value.reduce((sum, i) => sum + (Number(i.subtotal) || 0), 0).toFixed(2)
  }
  if (options.productId) {
    buyNowProductId.value = Number(options.productId)
    buyNowSpecId.value = Number(options.specId) || null
    buyNowQuantity.value = Number(options.quantity) || 1
    await loadBuyNowProduct()
  }
  await loadAddresses()
})

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
  if (buyNowProductId.value) {
    res = await createOrder({
      addressId: selectedAddress.value.id,
      remark: remark.value,
      orderItems: orderItems.value
    })
  } else if (cartData.value.length > 0) {
    const cartItemIds = cartData.value.map(item => item.id)
    res = await createOrder({
      addressId: selectedAddress.value.id,
      remark: remark.value,
      cartItemIds: cartItemIds
    })
  } else {
    uni.showToast({ title: '购物车数据异常', icon: 'none' })
    return
  }
  if (res.code === 0) {
    const { orderNo, payAmount } = res.data
    uni.redirectTo({
      url: `/pages/order/pay-success/pay-success?orderNo=${orderNo}&payAmount=${payAmount}`
    })
  } else {
    uni.showToast({ title: res.message || '下单失败', icon: 'none' })
  }
}
</script>

<style scoped>
.confirm-page {
  min-height: 100vh;
  background: #F0FDF4;
  padding-bottom: 140rpx;
}

.content {
  height: calc(100vh - 100rpx);
  overflow-y: auto;
}

.card {
  background: #FFFFFF;
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1px solid #BBF7D0;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
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
  color: #1F2937;
}

.phone {
  font-size: 26rpx;
  color: #6B7280;
}

.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  line-height: 1.4;
}

.tag-accent { background: #FFF7ED; color: #9A3412; }
.tag-success { background: #DCFCE7; color: #166534; }

.address-detail {
  font-size: 26rpx;
  color: #6B7280;
  margin-top: 8rpx;
  line-height: 1.5;
}

.address-empty {
  flex: 1;
  color: #9CA3AF;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.add-btn-text {
  color: #15803D;
  font-weight: 500;
}

.arrow-icon {
  width: 36rpx;
  height: 36rpx;
  color: #9CA3AF;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #14532D;
  margin-bottom: 20rpx;
  display: block;
}

.order-item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1px solid #E5E7EB;
}

.order-item:last-child { border-bottom: none; }

.item-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  background: #F0FDF4;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  margin-left: 16rpx;
  overflow: hidden;
}

.item-name {
  font-size: 26rpx;
  color: #1F2937;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-spec {
  font-size: 22rpx;
  color: #6B7280;
  margin-top: 4rpx;
}

.item-bottom {
  display: flex;
  align-items: baseline;
  margin-top: 8rpx;
}

.item-price {
  font-size: 26rpx;
  color: #A16207;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.item-price::before { content: '¥'; font-size: 20rpx; }

.item-qty {
  font-size: 24rpx;
  color: #6B7280;
  margin-left: 8rpx;
}

.item-subtotal {
  font-size: 28rpx;
  color: #1F2937;
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

.price-label { color: #6B7280; }
.price-value { color: #1F2937; font-variant-numeric: tabular-nums; }

.price-row.total {
  font-size: 32rpx;
  font-weight: 600;
}

.price-value.pay { color: #A16207; }

.divider {
  height: 1px;
  background: #E5E7EB;
  margin: 8rpx 0;
}

.remark-input {
  width: 100%;
  height: 88rpx;
  font-size: 28rpx;
  color: #1F2937;
  background: #F0FDF4;
  border-radius: 12rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border: 1px solid #D1D5DB;
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
  border-top: 1px solid #E5E7EB;
  z-index: 100;
}

.total-pay {
  flex: 1;
}

.pay-label { font-size: 26rpx; color: #6B7280; }

.pay-price {
  font-size: 40rpx;
  color: #A16207;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.pay-price::before { content: '¥'; font-size: 24rpx; }

.submit-btn {
  background: #15803D;
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
</style>
