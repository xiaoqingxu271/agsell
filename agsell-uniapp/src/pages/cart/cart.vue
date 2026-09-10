<template>
  <view class="cart-page">
    <view v-if="cartItems.length === 0" class="empty-cart">
      <view class="cart-empty">
        <view class="ce-handle"></view>
        <view class="ce-basket"></view>
        <view class="ce-wheel wl"></view>
        <view class="ce-wheel wr"></view>
      </view>
      <text class="empty-text">购物车是空的</text>
      <button class="go-shop-btn" @click="goToShop">去逛逛</button>
    </view>
    <view v-else>
      <view class="cart-list">
        <view
          v-for="item in cartItems"
          :key="item.id"
          class="cart-item card"
        >
          <view class="item-checkbox" :class="{ checked: item.valid !== 0 && item.selected === 1, disabled: item.valid === 0 }" @click="onToggleSelect(item)" role="checkbox" :aria-checked="item.selected === 1">
            <text class="checkbox-icon">{{ item.valid !== 0 && item.selected === 1 ? '✓' : '' }}</text>
          </view>
          <image
            class="item-image"
            :src="item.productImage || '/static/default-product.png'"
            mode="aspectFill"
            lazy-load
            @click="onProductTap(item.productId)"
            :alt="item.productName"
          />
          <view class="item-info">
            <text class="item-name">{{ item.productName }}</text>
            <text v-if="item.specName" class="item-spec">{{ item.specName }}</text>
            <view class="item-bottom">
              <template v-if="item.valid === 0">
                <text class="item-invalid">{{ item.invalidReason || '商品已失效' }}</text>
                <view class="item-delete" @click="onDelete(item)" role="button" aria-label="删除商品">
                  <view class="trash-icon">
                    <view class="trash-lid"></view>
                    <view class="trash-body"></view>
                  </view>
                </view>
              </template>
              <template v-else>
                <text class="item-price">¥{{ item.price }}</text>
                <view class="item-bottom-right">
                  <view class="qty-control">
                    <view class="qty-btn" @click="onMinus(item)" role="button" aria-label="减少数量">-</view>
                    <text class="qty-value">{{ item.quantity }}</text>
                    <view class="qty-btn" @click="onPlus(item)" role="button" aria-label="增加数量">+</view>
                  </view>
                  <view class="item-delete" @click="onDelete(item)" role="button" aria-label="删除商品">
                    <view class="trash-icon">
                      <view class="trash-lid"></view>
                      <view class="trash-body"></view>
                    </view>
                  </view>
                </view>
              </template>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部结算栏 -->
      <view class="bottom-bar">
        <view class="select-all" @click="onToggleAll" role="checkbox" :aria-checked="allSelected">
          <view class="checkbox-circle" :class="{ checked: allSelected }">
            <text v-if="allSelected" class="check-mark">✓</text>
          </view>
          <text class="select-text">全选</text>
        </view>
        <view class="total-info">
          <text class="total-label">合计：</text>
          <text class="total-price">¥{{ totalAmount }}</text>
        </view>
        <view
          class="checkout-btn"
          :class="{ disabled: selectedCount === 0 }"
          @click="onCheckout"
          role="button"
          aria-label="去结算"
        >
          去结算({{ selectedCount }})
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCartList, updateCartQuantity, deleteCartItem, toggleCartSelect, selectAllCart } from '../../api/cart'

const cartItems = ref([])
const allSelected = ref(false)

onShow(async () => {
  await loadCart()
})

async function loadCart() {
  const res = await getCartList()
  if (res.code === 0) {
    cartItems.value = res.data || []
    updateSelectAll()
  }
}

const selectedCount = computed(() => cartItems.value.filter(i => i.selected === 1 && i.valid !== 0).length)
const totalAmount = computed(() => {
  return cartItems.value
    .filter(i => i.selected === 1 && i.valid !== 0)
    .reduce((sum, i) => sum + (Number(i.subtotal) || 0), 0)
    .toFixed(2)
})

function updateSelectAll() {
  const validItems = cartItems.value.filter(i => i.valid !== 0)
  allSelected.value = validItems.length > 0 && validItems.every(i => i.selected === 1)
}

function onToggleSelect(item) {
  if (item.valid === 0) {
    uni.showToast({ title: item.invalidReason || '商品已失效', icon: 'none' })
    return
  }
  toggleCartSelect(item.id).then(() => loadCart())
}

function onToggleAll() {
  selectAllCart(!allSelected.value).then(() => loadCart())
}

function onPlus(item) {
  if (item.valid === 0) {
    uni.showToast({ title: item.invalidReason || '商品已失效', icon: 'none' })
    return
  }
  const maxStock = item.stock || 999
  if (item.quantity >= maxStock) {
    uni.showToast({ title: '库存不足（最多' + maxStock + '件）', icon: 'none' })
    return
  }
  updateCartQuantity(item.id, item.quantity + 1).then(() => loadCart())
}

function onMinus(item) {
  if (item.valid === 0) {
    uni.showToast({ title: item.invalidReason || '商品已失效', icon: 'none' })
    return
  }
  if (item.quantity <= 1) {
    onDelete(item)
    return
  }
  updateCartQuantity(item.id, item.quantity - 1).then(() => loadCart())
}

function onDelete(item) {
  uni.showModal({
    title: '提示',
    content: '确定删除该商品？',
    success: (res) => {
      if (res.confirm) deleteCartItem(item.id).then(() => loadCart())
    }
  })
}

function onProductTap(productId) {
  uni.navigateTo({ url: `/pages/product/product?id=${productId}` })
}

function onCheckout() {
  if (selectedCount.value === 0) {
    uni.showToast({ title: '请选择商品', icon: 'none' })
    return
  }
  const items = cartItems.value.filter(i => i.selected === 1 && i.valid !== 0)
  const safeItems = items.map(item => ({
    ...item,
    id: String(item.id),
    productId: String(item.productId),
    specId: item.specId ? String(item.specId) : null
  }))
  uni.navigateTo({
    url: `/pages/order/confirm/confirm?cartData=${encodeURIComponent(JSON.stringify(safeItems))}`
  })
}

function goToShop() {
  uni.switchTab({ url: '/pages/category/category' })
}
</script>

<style scoped>
.cart-page {
  min-height: 100vh;
  background: #F4F6F5;
  padding-bottom: 140rpx;
}

.empty-cart {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;
}

.cart-empty {
  position: relative;
  width: 120rpx;
  height: 120rpx;
  margin-bottom: 24rpx;
}

.ce-basket {
  position: absolute;
  left: 50%;
  top: 56%;
  transform: translate(-50%, -50%);
  width: 76rpx;
  height: 46rpx;
  border: 6rpx solid #C7CECB;
  border-top: none;
  border-radius: 0 0 10rpx 10rpx;
}

.ce-basket::before {
  content: '';
  position: absolute;
  left: 50%;
  top: -16rpx;
  transform: translateX(-50%);
  width: 88rpx;
  border-top: 6rpx solid #C7CECB;
  border-radius: 6rpx;
}

.ce-handle {
  position: absolute;
  left: 50%;
  top: 16%;
  transform: translateX(-50%);
  width: 30rpx;
  height: 30rpx;
  border: 6rpx solid #C7CECB;
  border-bottom: none;
  border-radius: 18rpx 18rpx 0 0;
}

.ce-wheel {
  position: absolute;
  top: 66%;
  width: 10rpx;
  height: 10rpx;
  border: 5rpx solid #C7CECB;
  border-radius: 50%;
  background: #FFFFFF;
}

.ce-wheel.wl { left: 16%; }
.ce-wheel.wr { right: 16%; }

.empty-text {
  font-size: 32rpx;
  color: #4B5563;
  margin-bottom: 40rpx;
}

.go-shop-btn {
  background: #15803D;
  color: #FFFFFF;
  border: none;
  border-radius: 44rpx;
  height: 88rpx;
  line-height: 88rpx;
  padding: 0 60rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.go-shop-btn::after {
  border: none;
}

.cart-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  margin: 16rpx 24rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
  transition: transform 120ms ease-out;
}

.cart-item:active {
  transform: scale(0.99);
}

.item-checkbox {
  width: 48rpx;
  height: 48rpx;
  margin-right: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  min-width: 48rpx;
}

.checkbox-icon {
  font-size: 32rpx;
  color: #D1D5DB;
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid #D1D5DB;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.item-checkbox.checked .checkbox-icon {
  color: #FFFFFF;
  background: #15803D;
  border-color: #15803D;
  font-weight: 600;
}

.item-checkbox.disabled .checkbox-icon {
  color: #E5E7EB;
  background: #F3F5F4;
  border-color: #E5E7EB;
}

.item-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  background: #F4F6F5;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  margin-left: 16rpx;
  overflow: hidden;
}

.item-name {
  font-size: 28rpx;
  color: #1F2937;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.item-spec {
  font-size: 22rpx;
  color: #6B7280;
  margin-top: 8rpx;
}

.item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}

.item-bottom-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.item-price {
  font-size: 32rpx;
  color: #A16207;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.qty-control {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.qty-btn {
  width: 56rpx;
  height: 56rpx;
  border: 1rpx solid #D1D5DB;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: #1F2937;
  background: #FFFFFF;
  min-width: 56rpx;
}

.qty-value {
  font-size: 28rpx;
  min-width: 48rpx;
  text-align: center;
  color: #1F2937;
  font-variant-numeric: tabular-nums;
}

.item-delete {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.trash-icon {
  position: relative;
  width: 36rpx;
  height: 36rpx;
}

.trash-lid {
  position: absolute;
  left: 50%;
  top: 9rpx;
  transform: translateX(-50%);
  width: 28rpx;
  border-top: 4rpx solid #9CA3AF;
  border-radius: 2rpx;
}

.trash-lid::before {
  content: '';
  position: absolute;
  left: 50%;
  top: -8rpx;
  transform: translateX(-50%);
  width: 12rpx;
  border-top: 4rpx solid #9CA3AF;
  border-radius: 2rpx;
}

.trash-body {
  position: absolute;
  left: 50%;
  top: 15rpx;
  transform: translateX(-50%);
  width: 22rpx;
  height: 18rpx;
  border: 4rpx solid #9CA3AF;
  border-top: none;
  border-radius: 0 0 4rpx 4rpx;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  height: 110rpx;
  padding: 0 24rpx;
  background: #FFFFFF;
  border-top: 1rpx solid #E3E7E5;
  box-sizing: border-box;
  z-index: 999;
}

.select-all {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-right: 24rpx;
  min-height: 88rpx;
}

.checkbox-circle {
  width: 40rpx;
  height: 40rpx;
  border: 2px solid #D1D5DB;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.checkbox-circle.checked {
  background: #15803D;
  border-color: #15803D;
}

.check-mark {
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1;
}

.select-text {
  font-size: 28rpx;
  color: #1F2937;
}

.total-info {
  flex: 1;
  text-align: center;
  padding-right: 24rpx;
}

.total-label {
  font-size: 26rpx;
  color: #6B7280;
}

.total-price {
  font-size: 32rpx;
  color: #A16207;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.checkout-btn {
  background: #15803D;
  color: #FFFFFF;
  padding: 0 48rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
  font-weight: 600;
  min-width: 88rpx;
  text-align: center;
}

.checkout-btn.disabled {
  background: #E5E7EB;
  color: #9CA3AF;
}

.item-invalid {
  font-size: 24rpx;
  color: #6B7280;
}
</style>
