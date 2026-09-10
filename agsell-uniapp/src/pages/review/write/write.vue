<template>
  <view class="review-write-page">
    <NavBar title="评价商品" @back="uni.navigateBack()" />

    <scroll-view scroll-y class="content">
      <!-- 商品信息（一单多商品可切换） -->
      <view class="product-info card">
        <view
          v-for="(item, idx) in items"
          :key="item.id"
          class="product-item"
          :class="{ active: idx === currentIndex }"
          @click="currentIndex = idx"
          role="button"
        >
          <image class="product-img" :src="item.productImage || '/static/default-product.png'" mode="aspectFill" lazy-load :alt="item.productName" />
          <view class="product-detail">
            <text class="product-name">{{ item.productName }}</text>
            <text v-if="item.specName" class="product-spec">{{ item.specName }}</text>
            <text class="product-price">¥{{ item.price }} × {{ item.quantity }}</text>
          </view>
          <view class="product-check" :class="{ checked: idx === currentIndex }">
            <text v-if="idx === currentIndex" class="check-mark">✓</text>
          </view>
        </view>
        <text v-if="items.length > 1" class="product-hint">该订单共 {{ items.length }} 件商品，点击切换要评价的商品</text>
      </view>

      <!-- 评分 -->
      <view class="rating-section card">
        <text class="section-title">商品满意度</text>
        <view class="rating-center">
          <StarRating :rating="rating" @change="onRatingChange" size="56rpx" />
          <text class="rating-text">{{ ratingText }}</text>
        </view>
      </view>

      <!-- 评价内容 -->
      <view class="content-section card">
        <text class="section-title">评价内容（最多500字）</text>
        <textarea
          class="review-textarea"
          v-model="content"
          placeholder="说说你的购物体验吧~"
          placeholder-style="color:#9CA3AF"
          maxlength="500"
          show-count
        />
      </view>

      <!-- 评价图片 -->
      <view class="content-section card">
        <text class="section-title">评价图片（最多9张，可选）</text>
        <view class="image-picker">
          <image
            v-for="(url, i) in imageUrls"
            :key="i"
            :src="url"
            mode="aspectFill"
            class="picked-img"
            @click="previewImage(i)"
            :alt="'评价图片' + (i + 1)"
          />
          <view v-if="imageUrls.length < 9" class="add-img-btn" @click="chooseImages" role="button" aria-label="添加图片">
            <text class="add-icon">+</text>
          </view>
        </view>
        <text class="image-hint">可上传最多9张实物图，让其他买家更好地了解商品</text>
      </view>

      <!-- 匿名评价 -->
      <view class="anonymous-section card">
        <view class="anonymous-row" @click="isAnonymous = isAnonymous ? 0 : 1" role="switch" :aria-checked="isAnonymous === 1">
          <view class="checkbox" :class="{ checked: isAnonymous === 1 }">
            <text v-if="isAnonymous === 1" class="check-mark">✓</text>
          </view>
          <text class="anonymous-label">匿名评价</text>
          <text class="anonymous-hint">评价后其他用户将看不到你的昵称</text>
        </view>
      </view>
    </scroll-view>

    <!-- 提交按钮 -->
    <view class="bottom-bar">
      <view class="submit-btn" @click="onSubmit" role="button">提交评价</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../../components/NavBar/NavBar.vue'
import StarRating from '../../../components/StarRating/StarRating.vue'
import { createReview } from '../../../api/review'
import { getOrderDetail } from '../../../api/order'
import { uploadMiniImage } from '../../../utils/upload'

const orderId = ref('')
const items = ref([])
const currentIndex = ref(0)
const rating = ref(0)
const content = ref('')
const isAnonymous = ref(0)
const imageUrls = ref([])

const ratingText = computed(() => {
  const texts = ['', '很差', '较差', '一般', '较好', '很好']
  return texts[rating.value] || ''
})

onLoad(async (options) => {
  const orderNo = options.orderNo || ''
  if (!orderNo) {
    uni.showToast({ title: '缺少订单号', icon: 'none' })
    return
  }
  const res = await getOrderDetail(orderNo)
  if (res.code === 0 && res.data) {
    orderId.value = res.data.id
    items.value = res.data.items || []
  } else {
    uni.showToast({ title: res.message || '订单加载失败', icon: 'none' })
  }
})

function onRatingChange(star) {
  rating.value = star
}

async function chooseImages() {
  uni.chooseImage({
    count: 9 - imageUrls.value.length,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      uni.showLoading({ title: '上传中...', mask: true })
      try {
        for (const filePath of res.tempFilePaths) {
          const url = await uploadMiniImage(filePath, 'review/image')
          imageUrls.value.push(url)
        }
      } catch {
      } finally {
        uni.hideLoading()
      }
    }
  })
}

function previewImage(index) {
  uni.previewImage({
    current: imageUrls.value[index],
    urls: imageUrls.value
  })
}

async function onSubmit() {
  const current = items.value[currentIndex.value]
  if (!current || !current.id) {
    uni.showToast({ title: '商品信息加载失败', icon: 'none' })
    return
  }
  if (rating.value === 0) {
    uni.showToast({ title: '请选择评分', icon: 'none' })
    return
  }
  if (!content.value.trim()) {
    uni.showToast({ title: '请输入评价内容', icon: 'none' })
    return
  }

  const res = await createReview({
    orderId: orderId.value,
    orderItemId: current.id,
    rating: rating.value,
    content: content.value.trim(),
    images: imageUrls.value.length > 0 ? imageUrls.value : undefined,
    isAnonymous: isAnonymous.value
  })

  if (res.code === 0) {
    uni.showToast({ title: '评价成功', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 1500)
  } else {
    uni.showToast({ title: res.message || '评价失败', icon: 'none' })
  }
}
</script>

<style scoped>
.review-write-page {
  min-height: 100vh;
  background: #F4F6F5;
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
  border: 1rpx solid #E3E7E5;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
}

.product-info {
  display: flex;
  flex-direction: column;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #EEF1EF;
  min-height: 88rpx;
  box-sizing: border-box;
}

.product-item.active {
  background: #F0FDF4;
  border-radius: 12rpx;
  padding: 12rpx 16rpx;
  margin: 0 -16rpx;
  border-bottom-color: transparent;
}

.product-item:last-child {
  border-bottom: none;
}

.product-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  background: #F4F6F5;
  flex-shrink: 0;
}

.product-detail {
  flex: 1;
  margin-left: 20rpx;
  overflow: hidden;
}

.product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2937;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-spec {
  font-size: 24rpx;
  color: #6B7280;
  margin-top: 8rpx;
  display: block;
}

.product-price {
  font-size: 24rpx;
  color: #A16207;
  margin-top: 8rpx;
  display: block;
  font-variant-numeric: tabular-nums;
}

.product-check {
  width: 40rpx;
  height: 40rpx;
  border: 2px solid #D1D5DB;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.product-check.checked {
  border-color: #15803D;
  background: #15803D;
}

.check-mark {
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1;
}

.product-hint {
  font-size: 22rpx;
  color: #6B7280;
  margin-top: 12rpx;
  display: block;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #15803D;
  margin-bottom: 20rpx;
  display: block;
}

.rating-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.rating-text {
  display: block;
  text-align: center;
  font-size: 28rpx;
  color: #A16207;
  font-weight: 600;
}

.review-textarea {
  width: 100%;
  min-height: 240rpx;
  font-size: 28rpx;
  color: #1F2937;
  background: #FFFFFF;
  border-radius: 12rpx;
  padding: 20rpx;
  box-sizing: border-box;
  border: 1rpx solid #D1D5DB;
  line-height: 1.6;
}

.image-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.picked-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  background: #F4F6F5;
}

.add-img-btn {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  border: 2rpx dashed #D1D5DB;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FAFAFA;
}

.add-icon {
  font-size: 56rpx;
  color: #9CA3AF;
  line-height: 1;
  font-weight: 300;
}

.image-hint {
  font-size: 22rpx;
  color: #6B7280;
  margin-top: 12rpx;
  display: block;
}

.anonymous-section .anonymous-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-height: 88rpx;
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  border: 2px solid #D1D5DB;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.checkbox.checked {
  border-color: #15803D;
  background: #15803D;
}

.anonymous-label {
  font-size: 28rpx;
  color: #1F2937;
}

.anonymous-hint {
  font-size: 22rpx;
  color: #6B7280;
  margin-left: auto;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #E3E7E5;
  z-index: 100;
}

.submit-btn {
  background: #15803D;
  color: #FFFFFF;
  text-align: center;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 32rpx;
  font-weight: 600;
}
</style>
