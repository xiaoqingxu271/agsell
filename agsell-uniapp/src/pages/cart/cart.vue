<template>
  <view class="cart-page">
    <!-- 加载骨架屏 -->
    <view v-if="loading" class="cart-loading">
      <view v-for="i in 3" :key="i" class="card sk-row">
        <wd-skeleton type="circle" width="48rpx" height="48rpx" />
        <wd-skeleton type="rect" width="160rpx" height="160rpx" margin-left="24rpx" />
        <view class="sk-col">
          <wd-skeleton type="rect" width="100%" height="32rpx" />
          <wd-skeleton type="rect" width="50%" height="28rpx" margin-top="32rpx" />
        </view>
      </view>
    </view>
    <view v-else-if="cartItems.length === 0" class="empty-cart">
      <wd-icon name="cart" size="140rpx" color="#C9CDD4" />
      <text class="empty-text">购物车是空的</text>
      <wd-button type="primary" round @click="goToShop">去逛逛</wd-button>
    </view>
    <view v-else>
      <!-- 领券提示条（浅红底 + 品牌红字） -->
      <view class="promo" @click="onCouponTap" role="button" aria-label="去领券中心">
        <wd-icon name="discount" size="28rpx" custom-class="promo-icon" />
        <text class="promo-text">结算前可领取优惠券，更划算</text>
        <view class="promo-go">
          <text>去领券</text>
          <wd-icon name="chevron-right" size="24rpx" custom-class="promo-arrow" />
        </view>
      </view>

      <view class="cart-list">
        <wd-swipe-action
          v-for="item in cartItems"
          :key="item.id"
          class="cart-swipe"
        >
          <view class="cart-item" :class="{ 'item-disabled': item.valid === 0 }">
            <wd-checkbox
              :model-value="item.valid !== 0 && item.selected === 1"
              :disabled="item.valid === 0"
              shape="circle"
              custom-class="item-check"
              @change="onToggleSelect(item)"
            />
            <view class="item-thumb" @click="onProductTap(item)">
              <image
                v-if="item.productImage"
                class="item-image"
                :src="item.productImage"
                mode="aspectFill"
                lazy-load
                :alt="item.productName"
              />
              <wd-icon v-else name="picture" size="72rpx" custom-class="item-ph-ic" />
            </view>
            <view class="item-info" @click="onProductTap(item)">
              <text class="item-name">{{ item.productName }}</text>
              <view v-if="item.specName" class="item-spec">
                <text class="spec-chip">{{ item.specName }}</text>
              </view>
              <view class="item-bottom">
                <template v-if="item.valid === 0">
                  <text class="item-invalid">{{ item.invalidReason || '商品已失效' }}</text>
                  <view class="item-delete" @click.stop="onDelete(item)" role="button" aria-label="删除商品">
                    <wd-icon name="delete" size="40rpx" color="#9AA0A8" />
                  </view>
                </template>
                <template v-else>
                  <view class="item-price">
                    <text class="price-symbol">¥</text>
                    <text class="price-int">{{ item.price }}</text>
                  </view>
                  <view class="qty-wrap" @click.stop>
                    <wd-input-number
                      :model-value="item.quantity"
                      :min="1"
                      :max="item.stock || 999"
                      input-width="64rpx"
                      @change="onQtyChange(item, $event)"
                    />
                  </view>
                </template>
              </view>
            </view>
          </view>
          <template #right>
            <view class="swipe-delete" @click="onDelete(item)">
              <wd-icon name="delete" size="44rpx" color="#FFFFFF" />
              <text class="swipe-delete-text">删除</text>
            </view>
          </template>
        </wd-swipe-action>
      </view>

      <!-- 底部结算栏 -->
      <view class="bottom-bar">
        <view class="select-all">
          <wd-checkbox :model-value="allSelected" shape="circle" @change="onToggleAll" />
          <text class="select-text">全选</text>
        </view>
        <view class="total-info">
          <text class="total-label">合计（已选 {{ selectedCount }} 件）</text>
          <view class="total-wrap">
            <text class="price-symbol">¥</text>
            <text class="total-price">{{ totalAmount }}</text>
          </view>
        </view>
        <wd-button
          type="error"
          round
          :disabled="selectedCount === 0"
          custom-style="width: 208rpx; height: 84rpx; background: #E1251B; border-color: #E1251B;"
          @click="onCheckout"
        >
          去结算({{ selectedCount }})
        </wd-button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getCartList, updateCartQuantity, deleteCartItem, toggleCartSelect, selectAllCart } from '../../api/cart'
import { setPlatformTitle } from '../../utils/request'

const cartItems = ref([])
const allSelected = ref(false)
const loading = ref(true)

onShow(async () => {
  setPlatformTitle()
  // 仅首次进入展示骨架屏；从下单页返回时静默刷新，避免闪烁
  loading.value = cartItems.value.length === 0
  try {
    await loadCart()
  } finally {
    loading.value = false
  }
})

async function loadCart() {
  const res = await getCartList()
  if (res.code === 0) {
    cartItems.value = res.data || []
    updateSelectAll()
  }
}

/** 下拉刷新：重新校验价格与库存，失效商品当场标灰 */
onPullDownRefresh(async () => {
  try {
    await loadCart()
  } catch (e) {
    console.warn('[Cart] 下拉刷新失败', e)
  } finally {
    uni.stopPullDownRefresh()
  }
})

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

function onQtyChange(item, { value }) {
  if (item.valid === 0) return
  const qty = Number(value)
  const maxStock = item.stock || 999
  if (!Number.isFinite(qty) || qty === item.quantity) return
  if (qty > maxStock) {
    uni.showToast({ title: '库存不足（最多' + maxStock + '件）', icon: 'none' })
    return
  }
  updateCartQuantity(item.id, qty).then(() => loadCart())
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

function onProductTap(item) {
  if (item.valid === 0) {
    uni.showToast({ title: item.invalidReason || '商品已失效', icon: 'none' })
    return
  }
  if (!item.productId) {
    uni.showToast({ title: '商品信息缺失', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/product?id=${item.productId}` })
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

function onCouponTap() {
  uni.navigateTo({ url: '/pages/coupon/center' })
}
</script>

<style lang="scss" scoped>
.cart-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
  padding-bottom: 200rpx;
}

/* 加载骨架屏 */
.cart-loading {
  padding-top: 16rpx;
}

.sk-row {
  display: flex;
  align-items: center;
  margin: 16rpx $uni-spacing-col-lg;
  padding: $uni-spacing-col-lg;
  background: $uni-bg-color;
  border-radius: $uni-border-radius-base;
  box-shadow: $uni-shadow-sm;
}

.sk-col {
  flex: 1;
  margin-left: $uni-spacing-col-lg;
}

.empty-cart {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;
}

.empty-text {
  font-size: 32rpx;
  color: $uni-text-color-grey;
  margin: $uni-spacing-col-lg 0 40rpx;
}

.cart-swipe {
  margin: 16rpx $uni-spacing-col-lg;
}

.cart-item {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
  padding: 24rpx;
  background: $uni-bg-color;
  border-radius: $uni-border-radius-base;
  box-shadow: $uni-shadow-sm;
  transition: opacity 150ms ease-out;
}

.cart-item:active {
  opacity: 0.85;
}

.item-disabled {
  opacity: 0.55;
}

.item-check {
  flex-shrink: 0;
  margin-top: 4rpx;
}

.item-thumb {
  width: 156rpx;
  height: 156rpx;
  border-radius: 12rpx;
  background: $uni-bg-color-img;
  flex-shrink: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.item-ph-ic) {
  color: $uni-color-img-icon;
}

.item-image {
  width: 100%;
  height: 100%;
}

.item-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  align-self: stretch;
}

.item-name {
  font-size: 24rpx;
  color: $uni-text-color;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 34rpx;
  height: 68rpx;
}

.item-spec {
  margin-top: 10rpx;
}

/* 规格胶囊：灰底小标签，点击无效 */
.spec-chip {
  display: inline-flex;
  align-items: center;
  font-size: 20rpx;
  color: $uni-text-color-grey;
  background: $uni-bg-color-grey;
  border-radius: 6rpx;
  padding: 4rpx 12rpx;
  line-height: 1.4;
}

.item-bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 16rpx;
}

.item-price,
.total-wrap {
  display: flex;
  align-items: baseline;
  gap: 2rpx;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
}

.price-symbol {
  font-size: 22rpx;
  font-weight: 700;
  color: $uni-color-price;
}

.price-int {
  font-size: 30rpx;
  font-weight: 700;
  color: $uni-color-price;
  line-height: 1;
  letter-spacing: -1rpx;
}

.qty-wrap {
  flex-shrink: 0;
}

.item-delete {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.swipe-delete {
  width: 140rpx;
  height: 100%;
  background: $uni-color-error;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
}

.swipe-delete-text {
  font-size: $uni-font-size-caption;
  color: $uni-text-color-inverse;
}

/* 领券提示条 */
.promo {
  margin: 16rpx 24rpx 0;
  padding: 18rpx 24rpx;
  border-radius: $uni-border-radius-base;
  background: $uni-color-primary-50;
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 24rpx;
  color: $uni-color-primary;
}

:deep(.promo-icon) {
  color: $uni-color-primary;
}

.promo-text {
  flex: 1;
  min-width: 0;
}

.promo-go {
  display: flex;
  align-items: center;
  gap: 2rpx;
  font-weight: 600;
  flex-shrink: 0;
}

:deep(.promo-arrow) {
  color: $uni-color-primary;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 116rpx;
  padding: 0 20rpx 0 24rpx;
  padding-bottom: env(safe-area-inset-bottom);
  background: $uni-bg-color;
  border-top: 2rpx solid $uni-border-color-divider;
  box-sizing: content-box;
  z-index: 999;
}

.select-all {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-shrink: 0;
}

.select-text {
  font-size: $uni-font-size-sm;
  color: $uni-text-color;
}

.total-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  padding-right: $uni-spacing-col-base;
}

.total-label {
  font-size: 20rpx;
  color: $uni-text-color-grey;
}

.total-price {
  font-size: 40rpx;
  color: $uni-color-price;
  font-weight: 700;
  line-height: 1;
}

.item-invalid {
  font-size: $uni-font-size-sm;
  color: $uni-text-color-grey;
}
</style>
