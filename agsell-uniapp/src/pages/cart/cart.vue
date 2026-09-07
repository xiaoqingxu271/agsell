<template>
  <view class="cart-page">
    <view v-if="cartItems.length === 0" class="empty-cart">
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
          <view class="item-checkbox" :class="{ disabled: item.valid === 0 }" @click="onToggleSelect(item)">
            <text class="checkbox-icon">{{ item.valid !== 0 && item.selected === 1 ? '✓' : '' }}</text>
          </view>
          <image
            class="item-image"
            :src="item.productImage || '/static/default-product.png'"
            mode="aspectFill"
            @click="onProductTap(item.productId)"
          />
          <view class="item-info">
            <text class="item-name">{{ item.productName }}</text>
            <text v-if="item.specName" class="item-spec">{{ item.specName }}</text>
            <view class="item-bottom">
              <text v-if="item.valid === 0" class="item-invalid">{{ item.invalidReason || '商品已失效' }}</text>
              <template v-else>
                <text class="item-price">¥{{ item.price }}</text>
                <view class="qty-control">
                  <view class="qty-btn" @click="onMinus(item)">-</view>
                  <text class="qty-value">{{ item.quantity }}</text>
                  <view class="qty-btn" @click="onPlus(item)">+</view>
                </view>
              </template>
            </view>
          </view>
          <view class="item-delete" @click="onDelete(item)">
            <text class="delete-icon">×</text>
          </view>
        </view>
      </view>

      <!-- 底部结算栏 -->
      <view class="bottom-bar">
        <view class="select-all" @click="onToggleAll">
          <text class="checkbox-icon">{{ allSelected ? '✓' : '' }}</text>
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
    uni.showToast({ title: `库存不足（最多${maxStock}件）`, icon: 'none' })
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
  // 将ID转为字符串避免精度丢失，然后序列化为JSON
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
  background: #f5f5f5;
  padding-bottom: 120rpx;
}

.empty-cart {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;
}

.empty-text {
  font-size: 32rpx;
  color: #999;
  margin-bottom: 40rpx;
}

.go-shop-btn {
  background: #4CAF50;
  color: #fff;
  border: none;
  border-radius: 44rpx;
  padding: 20rpx 60rpx;
  font-size: 28rpx;
}

.cart-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  margin: 20rpx;
  background: #fff;
  border-radius: 16rpx;
}

.item-checkbox {
  width: 48rpx;
  margin-right: 16rpx;
}

.checkbox-icon {
  font-size: 32rpx;
  color: #ccc;
}

.item-checkbox.checked .checkbox-icon,
.cart-item.selected .checkbox-icon {
  color: #4CAF50;
  font-weight: bold;
}

.item-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  background: #f5f5f5;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  margin-left: 16rpx;
  overflow: hidden;
}

.item-name {
  font-size: 28rpx;
  color: #333;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.item-spec {
  font-size: 22rpx;
  color: #999;
  margin-top: 8rpx;
}

.item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}

.item-price {
  font-size: 32rpx;
  color: #FF9800;
  font-weight: bold;
}

.item-price::before { content: '¥'; font-size: 22rpx; }

.qty-control {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.qty-btn {
  width: 48rpx;
  height: 48rpx;
  border: 1rpx solid #ccc;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: #666;
}

.qty-value {
  font-size: 28rpx;
  min-width: 48rpx;
  text-align: center;
}

.item-delete {
  width: 48rpx;
  text-align: center;
  margin-left: 16rpx;
}

.delete-icon {
  font-size: 40rpx;
  color: #ccc;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  height: 100rpx;
  padding: 0 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
  z-index: 100;
}

.select-all {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-right: 24rpx;
}

.select-text {
  font-size: 28rpx;
  color: #333;
}

.total-info {
  flex: 1;
  text-align: right;
}

.total-label {
  font-size: 26rpx;
  color: #666;
}

.total-price {
  font-size: 36rpx;
  color: #FF9800;
  font-weight: bold;
}

.total-price::before { content: '¥'; font-size: 24rpx; }

.checkout-btn {
  background: #4CAF50;
  color: #fff;
  padding: 16rpx 48rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
}

.checkout-btn.disabled {
  background: #ccc;
}

.item-checkbox.disabled .checkbox-icon {
  color: #ccc;
  background: #f2f2f2;
}

.item-invalid {
  font-size: 24rpx;
  color: #999;
}

.cart-item .item-info {
  opacity: 1;
}
</style>
