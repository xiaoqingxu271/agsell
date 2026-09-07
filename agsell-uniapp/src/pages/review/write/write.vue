<template>
  <view class="review-write-page">
    <NavBar title="评价商品" />

    <scroll-view scroll-y class="content">
      <!-- 商品信息（一单多商品可切换） -->
      <view class="product-info card">
        <view
          v-for="(item, idx) in items"
          :key="item.id"
          class="product-item"
          :class="{ active: idx === currentIndex }"
          @click="currentIndex = idx"
        >
          <image class="product-img" :src="item.productImage || '/static/default-product.png'" mode="aspectFill" />
          <view class="product-detail">
            <text class="product-name">{{ item.productName }}</text>
            <text v-if="item.specName" class="product-spec">{{ item.specName }}</text>
            <text class="product-price">¥{{ item.price }} × {{ item.quantity }}</text>
          </view>
          <text class="product-check">{{ idx === currentIndex ? '✓' : '' }}</text>
        </view>
        <text v-if="items.length > 1" class="product-hint">该订单共 {{ items.length }} 件商品，点击切换要评价的商品</text>
      </view>

      <!-- 评分 -->
      <view class="rating-section card">
        <view class="section-title">商品满意度</view>
        <StarRating :rating="rating" @change="onRatingChange" />
        <text class="rating-text">{{ ratingText }}</text>
      </view>

      <!-- 评价内容 -->
      <view class="content-section card">
        <view class="section-title">评价内容（最多500字）</view>
        <textarea
          class="review-textarea"
          v-model="content"
          placeholder="说说你的购物体验吧~"
          maxlength="500"
          show-count
        />
      </view>

      <!-- 评价图片 -->
      <view class="content-section card">
        <view class="section-title">评价图片（最多9张，可选）</view>
        <view class="image-picker">
          <image
            v-for="(url, i) in imageUrls"
            :key="i"
            :src="url"
            mode="aspectFill"
            class="picked-img"
            @click="previewImage(i)"
          />
          <view v-if="imageUrls.length < 9" class="add-img-btn" @click="chooseImages">
            <text class="add-icon">+</text>
          </view>
        </view>
        <text class="image-hint">可上传最多9张实物图，让其他买家更好地了解商品</text>
      </view>

      <!-- 匿名评价 -->
      <view class="anonymous-section card">
        <view class="anonymous-row" @click="isAnonymous = isAnonymous ? 0 : 1">
          <text class="checkbox" :class="{ checked: isAnonymous === 1 }">
            {{ isAnonymous === 1 ? '✓' : '' }}
          </text>
          <text class="anonymous-label">匿名评价</text>
          <text class="anonymous-hint">评价后其他用户将看不到你的昵称</text>
        </view>
      </view>
    </scroll-view>

    <!-- 提交按钮 -->
    <view class="bottom-bar">
      <view class="submit-btn" @click="onSubmit">提交评价</view>
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
        // error already shown inside uploadMiniImage
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

.product-info {
  display: flex;
  flex-direction: column;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
}

.product-item.active {
  background: #f7fff7;
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
  background: #f5f5f5;
  flex-shrink: 0;
}

.product-detail {
  flex: 1;
  margin-left: 20rpx;
  overflow: hidden;
}

.product-name {
  font-size: 28rpx;
  font-weight: bold;
  color: #333;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-spec {
  font-size: 24rpx;
  color: #999;
  margin-top: 8rpx;
  display: block;
}

.product-price {
  font-size: 24rpx;
  color: #FF9800;
  margin-top: 8rpx;
  display: block;
}

.product-check {
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid #ccc;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: transparent;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.product-item.active .product-check {
  border-color: #4CAF50;
  color: #4CAF50;
  background: #f0fff0;
}

.product-hint {
  font-size: 22rpx;
  color: #999;
  margin-top: 12rpx;
  display: block;
}

.section-title {
  font-size: 30rpx;
  font-weight: bold;
  color: #333;
  margin-bottom: 20rpx;
}

.rating-text {
  display: block;
  text-align: center;
  margin-top: 16rpx;
  font-size: 28rpx;
  color: #FF9800;
  font-weight: bold;
}

.review-textarea {
  width: 100%;
  height: 240rpx;
  font-size: 28rpx;
  color: #333;
  background: #f5f5f5;
  border-radius: 12rpx;
  padding: 20rpx;
  box-sizing: border-box;
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
  background: #f5f5f5;
}

.add-img-btn {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  border: 2rpx dashed #ccc;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
}

.add-icon {
  font-size: 48rpx;
  color: #ccc;
}

.image-hint {
  font-size: 22rpx;
  color: #999;
  margin-top: 12rpx;
  display: block;
}

.anonymous-section .anonymous-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid #ccc;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: transparent;
}

.checkbox.checked {
  border-color: #4CAF50;
  color: #4CAF50;
  background: #f0fff0;
}

.anonymous-label {
  font-size: 28rpx;
  color: #333;
}

.anonymous-hint {
  font-size: 22rpx;
  color: #999;
  margin-left: auto;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20rpx 24rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
  z-index: 100;
}

.submit-btn {
  background: #4CAF50;
  color: #fff;
  text-align: center;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 32rpx;
}
</style>
