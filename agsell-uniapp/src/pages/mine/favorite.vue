<template>
  <view class="page">
    <NavBar title="我的收藏" show-back @back="onBack" />

    <scroll-view class="list" scroll-y>
      <template v-if="favorites.length">
        <view v-for="item in favorites" :key="item.id" class="fav-card">
          <view class="fav-main" @click="goProduct(item)" role="button" :aria-label="'查看 ' + item.name">
            <image class="fav-img" :src="item.mainImage || '/static/default-product.png'" mode="aspectFill" lazy-load />
            <view class="fav-in">
              <text class="fav-name">{{ item.name }}</text>
              <view class="fav-prow">
                <view class="fav-p">
                  <text class="p-symbol">¥</text>
                  <text class="p-int">{{ item.price }}</text>
                </view>
                <text class="fav-sales">已售 {{ item.sales || 0 }}</text>
              </view>
              <text v-if="item.productStatus === 0" class="fav-off">已下架</text>
            </view>
          </view>
          <view class="fav-del" @click.stop="onRemove(item)" role="button" aria-label="取消收藏">
            <wd-icon name="heart-filled" size="30rpx" custom-class="fav-heart" />
          </view>
        </view>
        <view class="bottom-safe"></view>
      </template>

      <view v-else-if="!loading" class="empty">
        <wd-icon name="heart" size="88rpx" custom-class="empty-icon" />
        <text class="empty-t">还没有收藏的商品</text>
        <text class="empty-s">逛一逛，看到喜欢的心形一点收进来</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getFavorites, removeFavorite } from '../../api/favorite'
import { isLoggedIn, wxLogin } from '../../utils/request'

const favorites = ref([])
const loading = ref(true)

onShow(async () => {
  // 未登录先静默登录（与商详收藏按钮同一体验），失败给空态
  if (!isLoggedIn()) {
    await wxLogin()
  }
  await loadFavorites()
})

onPullDownRefresh(async () => {
  await loadFavorites()
  uni.stopPullDownRefresh()
})

async function loadFavorites() {
  loading.value = true
  try {
    const res = await getFavorites(1, 50)
    if (res.code === 0) {
      favorites.value = res.data?.records || []
    }
  } catch (e) {
    console.warn('[Favorite] 收藏列表加载失败', e)
  } finally {
    loading.value = false
  }
}

function goProduct(item) {
  uni.navigateTo({ url: `/pages/product/product?id=${item.productId}` })
}

async function onRemove(item) {
  try {
    const res = await removeFavorite(item.productId)
    if (res.code === 0) {
      favorites.value = favorites.value.filter((f) => f.id !== item.id)
      uni.showToast({ title: '已取消收藏', icon: 'none' })
    } else {
      uni.showToast({ title: res.message || '操作失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: '网络异常，请稍后再试', icon: 'none' })
  }
}

function onBack() {
  uni.navigateBack({ delta: 1 })
}
</script>

<style lang="scss" scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: $uni-bg-color-grey;
  overflow: hidden;
}

.list {
  flex: 1;
  height: 0;
  padding: 24rpx;
  box-sizing: border-box;
}

.fav-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  margin-bottom: 20rpx;
  background: $uni-bg-color;
  border-radius: 16rpx;
  box-shadow: $uni-shadow-sm;
}

.fav-main {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 20rpx;
}

.fav-img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 12rpx;
  background: $uni-bg-color-img;
  flex-shrink: 0;
}

.fav-in {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.fav-name {
  font-size: 26rpx;
  line-height: 36rpx;
  color: $uni-text-color;
  font-weight: 600;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.fav-prow {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.fav-p {
  color: $uni-color-primary;
}

.p-symbol {
  font-size: 22rpx;
  font-weight: 600;
}

.p-int {
  font-size: 32rpx;
  font-weight: 700;
}

.fav-sales {
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
}

.fav-off {
  font-size: 20rpx;
  color: $uni-color-primary;
}

.fav-del {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

:deep(.fav-heart) {
  color: $uni-color-primary;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;
  gap: 12rpx;
}

:deep(.empty-icon) {
  color: $uni-text-color-disable;
}

.empty-t {
  font-size: 28rpx;
  color: $uni-text-color-grey;
}

.empty-s {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
}

.bottom-safe {
  height: calc(24rpx + env(safe-area-inset-bottom));
}
</style>
