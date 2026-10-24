<template>
  <view class="index-page">
    <!-- 定位 + 扫码搜索 + 消息（白底头部） -->
    <view class="hd">
      <view class="loc" @click="onCityTap" role="button" aria-label="选择城市">
        <wd-icon name="location" size="26rpx" custom-class="loc-pin" />
        <text class="loc-city">{{ cityName }}</text>
        <wd-icon name="chevron-down" size="20rpx" custom-class="loc-arrow" />
      </view>
      <view class="srch" @click="onSearchTap" role="button" aria-label="搜索商品">
        <wd-icon name="scan" size="28rpx" custom-class="srch-scan" />
        <text class="srch-ph">{{ searchPlaceholder }}</text>
        <view class="srch-go">搜索</view>
      </view>
      <view class="hd-ic" @click="onNotifyTap" role="button" aria-label="消息通知">
        <wd-icon name="notification" size="36rpx" custom-class="hd-bell" />
        <view v-if="unreadCount > 0" class="bell-dot" aria-label="有未读消息"></view>
      </view>
    </view>

    <!-- 分类频道宫格（5 列） -->
    <view v-if="categories.length > 0" class="cats">
      <view
        v-for="(cat, idx) in categories"
        :key="cat.id"
        class="cat"
        :class="{ on: idx === 0 }"
        @click="onCategoryTap(cat)"
        role="button"
        :aria-label="cat.name"
      >
        <view class="cat-box" :class="`cat-c${(idx % 10) + 1}`">
          <image v-if="cat.icon" class="cat-img" :src="cat.icon" mode="aspectFill" lazy-load :alt="cat.name" />
          <text v-else class="cat-char">{{ cat.name[0] }}</text>
        </view>
        <text class="cat-name">{{ cat.name }}</text>
      </view>
    </view>

    <!-- 大促 Banner -->
    <swiper
      v-if="banners.length > 0"
      class="bn"
      indicator-dots
      autoplay
      circular
      interval="5000"
      indicator-color="rgba(255,255,255,0.35)"
      indicator-active-color="#FFFFFF"
    >
      <swiper-item v-for="banner in banners" :key="banner.id">
        <view class="bn-slide bn-ph" @click="onBannerTap(banner)">
          <text class="bn-tag">产地直发</text>
          <text class="bn-title">{{ banner.title || '秋收尝鲜季' }}</text>
          <text class="bn-sub">全国 8 大产区 · 满 99 减 20</text>
          <text class="bn-cta">立即抢购</text>
          <wd-icon name="bags" size="164rpx" custom-class="bn-art" />
          <image
            v-if="banner.image"
            class="bn-image"
            :src="banner.image"
            mode="aspectFill"
            lazy-load
            :alt="banner.title || '轮播图'"
          />
        </view>
      </swiper-item>
    </swiper>
    <view v-else-if="!loading" class="bn bn-ph">
      <text class="bn-tag">产地直发</text>
      <text class="bn-title">秋收尝鲜季</text>
      <text class="bn-sub">全国 8 大产区 · 满 99 减 20</text>
      <text class="bn-cta">立即抢购</text>
      <wd-icon name="bags" size="164rpx" custom-class="bn-art" />
    </view>

    <!-- 限时秒杀（白底模块 + 倒计时 + 已抢进度） -->
    <view v-if="seckillActivities.length > 0" class="blk">
      <view class="bhd" @click="onSeckillMoreTap" role="button" aria-label="查看全部秒杀">
        <wd-icon name="time" size="30rpx" custom-class="bhd-icon" />
        <text class="bhd-title">限时秒杀</text>
        <view v-if="countdownSegments.length" class="cd">
          <template v-for="(seg, i) in countdownSegments" :key="i">
            <text v-if="i > 0" class="cd-sep">:</text>
            <text class="cd-num">{{ seg }}</text>
          </template>
        </view>
      </view>
      <scroll-view scroll-x class="sk-scroll">
        <view class="sk-inner">
          <view
            v-for="act in seckillActivities"
            :key="act.id"
            class="sk-item"
            @click="onSeckillItemTap(act)"
            role="button"
            :aria-label="act.productName"
          >
            <view class="sk-thumb">
              <image class="sk-img" :src="act.productImage" mode="aspectFill" lazy-load :alt="act.productName" />
              <text v-if="act.activityStatus === 1" class="sk-ribbon">即将开始</text>
            </view>
            <text class="sk-name">{{ act.productName }}</text>
            <view class="sk-price">
              <text class="p-symbol">¥</text>
              <text class="p-int">{{ act.seckillPrice }}</text>
              <text class="p-origin">¥{{ act.productPrice }}</text>
            </view>
            <view class="bar">
              <view class="bar-fill" :style="{ width: `${act.progress || 0}%` }"></view>
              <text class="bar-txt">已抢{{ act.progress || 0 }}%</text>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 服务承诺条 -->
    <view class="svc">
      <view class="svc-item"><wd-icon name="secured" size="26rpx" custom-class="svc-icon" /><text>坏果包赔</text></view>
      <view class="svc-item"><wd-icon name="bags" size="26rpx" custom-class="svc-icon" /><text>顺丰包邮</text></view>
      <view class="svc-item"><wd-icon name="qrcode" size="26rpx" custom-class="svc-icon" /><text>产地溯源</text></view>
    </view>

    <!-- 推荐流（定高双列，与参考稿一致只保留灰字说明） -->
    <view class="sec-t">
      <view class="sec-t-bar"></view>
      <text class="sec-t-title">为你推荐</text>
      <text class="sec-t-hint">根据你的浏览偏好</text>
    </view>
    <view class="flow">
      <view v-if="loading && !hotProducts.length" class="flow-skeleton">
        <view v-for="n in 4" :key="n" class="skeleton-card">
          <wd-skeleton type="rect" width="100%" height="304rpx" border-radius="16rpx 16rpx 0 0" />
          <view class="skeleton-lines">
            <wd-skeleton type="text" width="86%" height="26rpx" />
            <wd-skeleton type="text" width="52%" height="34rpx" margin-top="14rpx" />
          </view>
        </view>
      </view>
      <template v-else-if="hotProducts.length">
        <ProductCard v-for="item in hotProducts" :key="item.id" :product="item" @tap="onProductTap" @add="onQuickAdd" />
      </template>
      <view v-else class="flow-empty">
        <text>暂无推荐商品</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import ProductCard from '../../components/ProductCard/ProductCard.vue'
import { getBannerList } from '../../api/banner'
import { getCategoryList, getHotProducts, getRecommendFeed } from '../../api/product'
import { getSeckillList } from '../../api/seckill'
import { addToCart, getCartList } from '../../api/cart'
import { getUnreadCount } from '../../api/message'
import { isLoggedIn, wxLogin, request } from '../../utils/request'

const banners = ref([])
const categories = ref([])
const hotProducts = ref([])
const seckillActivities = ref([])
const loading = ref(true)
const unreadCount = ref(0)

/** 首页城市定位（演示环境无定位接口，固定展示产区城市） */
const cityName = '南昌'

/** 秒杀倒计时：以最近结束的进行中活动为基准，本地每秒递减 */
const seckillCountdown = ref(null)
let countdownTimer = null

onMounted(async () => {
  setNavTitle()
  try {
    await loadAll()
  } finally {
    loading.value = false
  }
  syncCartBadge()
})

/** 下拉刷新：让刚发生的浏览行为立刻反映到「为你推荐」，无需退出重进 */
onPullDownRefresh(async () => {
  await loadAll()
  syncCartBadge()
  syncMessageBadge()
  uni.stopPullDownRefresh()
})

/** 并行加载且互不阻断：某个接口失败时其余模块照常渲染，页面不白屏 */
async function loadAll() {
  await Promise.allSettled([loadBanners(), loadSeckill(), loadCategories(), loadHotProducts()])
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

/** 消息红点：进首页、下拉刷新、从消息中心返回都会同步；游客/接口失败静默为无红点 */
onShow(() => {
  syncMessageBadge()
})

async function syncMessageBadge() {
  if (!isLoggedIn()) {
    unreadCount.value = 0
    return
  }
  try {
    const res = await getUnreadCount()
    if (res.code === 0) unreadCount.value = res.data || 0
  } catch (e) {
    // 红点是装饰性功能，失败不打扰用户
  }
}

const searchPlaceholder = computed(() => {
  const name = hotProducts.value[0]?.name
  return name ? name.slice(0, 12) : '搜索商品'
})

const countdownSegments = computed(() => {
  const total = seckillCountdown.value
  if (total === null || total < 0) return []
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return [h, m, s].map((n) => String(n).padStart(2, '0'))
})

/** 首页导航栏展示系统配置的平台名称（管理端可改），失败时保留 pages.json 默认标题 */
async function setNavTitle() {
  try {
    const cached = uni.getStorageSync('platform_name')
    if (cached) uni.setNavigationBarTitle({ title: cached })
    const res = await request('GET', '/system/config', null, { params: { keys: 'platform_name' } })
    if (res.code === 0 && res.data?.platform_name) {
      uni.setStorageSync('platform_name', res.data.platform_name)
      uni.setNavigationBarTitle({ title: res.data.platform_name })
    }
  } catch (e) {
    console.warn('[Index] 加载平台名称失败，使用默认标题', e)
  }
}

async function loadSeckill() {
  const res = await getSeckillList({ filter: 0 })
  if (res.code === 0) {
    seckillActivities.value = res.data || []
    startCountdown(seckillActivities.value)
  }
}

/** 取进行中最快结束的活动倒计时；没有进行中活动则取最近一场未开始活动 */
function startCountdown(list) {
  const running = list.filter((a) => a.activityStatus === 2 && a.countdownSeconds > 0)
  const upcoming = list.filter((a) => a.activityStatus === 1 && a.countdownSeconds > 0)
  const target = running.length
    ? running.reduce((min, a) => (a.countdownSeconds < min.countdownSeconds ? a : min))
    : upcoming.length
      ? upcoming.reduce((min, a) => (a.countdownSeconds < min.countdownSeconds ? a : min))
      : null
  if (!target) return
  let remain = target.countdownSeconds
  seckillCountdown.value = remain
  countdownTimer = setInterval(() => {
    remain -= 1
    seckillCountdown.value = remain > 0 ? remain : null
    if (remain <= 0) clearInterval(countdownTimer)
  }, 1000)
}

async function loadBanners() {
  const res = await getBannerList()
  if (res.code === 0) banners.value = res.data
}

async function loadCategories() {
  const res = await getCategoryList()
  if (res.code === 0) {
    categories.value = res.data.filter(c => !c.parentId || String(c.parentId) === '0')
  }
}

async function loadHotProducts() {
  // AI 个性化 feed（登录用户按偏好+销量+好评排序；游客/服务不可用时 Java 侧回退热销榜）
  // 前端再兜一层：feed 接口本身失败（旧版后端无此接口等）→ 直接拉热销
  const res = await getRecommendFeed(10)
  if (res.code === 0 && res.data && res.data.length) {
    hotProducts.value = res.data
    return
  }
  const fallback = await getHotProducts(10)
  if (fallback.code === 0) hotProducts.value = fallback.data
}

function onCategoryTap(cat) {
  uni.switchTab({ url: '/pages/category/category' })
}

function onProductTap(product) {
  const id = String(product?.id || '')
  if (!id || id === 'undefined' || id === 'null') {
    uni.showToast({ title: '商品ID无效', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/product?id=${id}` })
}

function onBannerTap(banner) {
  if (banner.link) {
    if (banner.link.startsWith('/product/')) {
      const id = banner.link.replace('/product/', '')
      uni.navigateTo({ url: `/pages/product/product?id=${id}` })
    } else if (banner.link.startsWith('/product/list')) {
      uni.switchTab({ url: '/pages/category/category' })
    }
  }
}

function onSearchTap() {
  uni.navigateTo({ url: '/pages/search/search' })
}

function onCityTap() {
  uni.showToast({ title: '演示环境暂不支持切换城市', icon: 'none' })
}

async function onNotifyTap() {
  // 消息中心：发货/售后处理结果的站内信（未登录先静默登录，与其他入口同一体验）
  if (!isLoggedIn()) {
    const success = await wxLogin()
    if (!success) return
  }
  uni.navigateTo({ url: '/pages/mine/message' })
}

/** 商卡右下角红色加购：与详情页加购走同一接口，规格由后端取默认 */
async function onQuickAdd(product) {
  if (!isLoggedIn()) {
    const success = await wxLogin()
    if (!success) return
  }
  const res = await addToCart({ productId: product.id, specId: null, quantity: 1 })
  if (res.code === 0) {
    uni.showToast({ title: '已加入购物车', icon: 'success' })
    syncCartBadge()
  } else {
    uni.showToast({ title: res.message || '操作失败', icon: 'none' })
  }
}

/** 参考稿购物车 tab 带件数角标 */
async function syncCartBadge() {
  if (!isLoggedIn()) return
  try {
    const res = await getCartList()
    const count = (res.data || []).reduce((n, it) => n + (it.quantity || 1), 0)
    if (count > 0) {
      uni.setTabBarBadge({ index: 2, text: String(count) })
    } else {
      uni.removeTabBarBadge({ index: 2 })
    }
  } catch (e) {
    console.warn('[Index] 购物车角标同步失败', e)
  }
}

function onSeckillMoreTap() {
  uni.navigateTo({ url: '/pages/seckill/list' })
}

function onSeckillItemTap(act) {
  const code = String(act?.activityCode || '')
  if (!code || code === 'undefined' || code === 'null') {
    uni.showToast({ title: '活动编号无效', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/seckill/detail?code=${code}` })
}
</script>

<style lang="scss" scoped>
.index-page {
  background: $uni-bg-color-grey;
  min-height: 100vh;
  padding-bottom: 32rpx;
}

/* ===== 搜索头部（白底） ===== */
.hd {
  background: $uni-bg-color;
  padding: 12rpx 24rpx 20rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.loc {
  display: flex;
  align-items: center;
  gap: 4rpx;
  flex-shrink: 0;
}

.loc-city {
  font-size: 24rpx;
  font-weight: 500;
  color: $uni-text-color;
}

:deep(.loc-pin) {
  color: $uni-color-primary;
}

:deep(.loc-arrow) {
  color: $uni-text-color-placeholder;
}

.hd-ic {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.bell-dot {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $uni-color-primary;
}

:deep(.hd-bell) {
  color: $uni-text-color-grey;
}

.srch {
  flex: 1;
  min-width: 0;
  height: 64rpx;
  background: $uni-bg-color-muted;
  border: 1rpx solid #E8E9EC;
  border-radius: 32rpx;
  display: flex;
  align-items: center;
  padding: 0 8rpx 0 20rpx;
  gap: 12rpx;
}

:deep(.srch-scan) {
  color: $uni-text-color-placeholder;
  flex-shrink: 0;
}

.srch-ph {
  flex: 1;
  font-size: 24rpx;
  color: $uni-text-color-placeholder;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.srch-go {
  height: 48rpx;
  padding: 0 24rpx;
  border-radius: 24rpx;
  background: $uni-color-primary;
  color: $uni-text-color-inverse;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  font-weight: 500;
}

/* ===== 分类宫格 ===== */
.cats {
  background: $uni-bg-color;
  padding: 24rpx 8rpx 32rpx;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  row-gap: 28rpx;
  border-bottom-left-radius: $uni-border-radius-lg;
  border-bottom-right-radius: $uni-border-radius-lg;
}

.cat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.cat-box {
  width: 76rpx;
  height: 76rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cat-img {
  width: 76rpx;
  height: 76rpx;
}

.cat-char {
  font-size: 36rpx;
  font-weight: 500;
  line-height: 1;
}

.cat-name {
  font-size: 22rpx;
  color: $uni-text-color-grey;
  line-height: 1;
}

.cat.on .cat-name {
  color: $uni-color-primary;
  font-weight: 600;
}

/* 宫格底色：分类图标按位次循环取色（品质电商风 v5.0） */
.cat-c1 { background: #FFF1EF; color: #F04B3E; }
.cat-c2 { background: #FFF5E8; color: #F08A00; }
.cat-c3 { background: #EAF7F0; color: #00A66A; }
.cat-c4 { background: #EAF2FD; color: #2E7CE4; }
.cat-c5 { background: #F2EEFB; color: #7B57D8; }
.cat-c6 { background: #E9F6F8; color: #0E97A8; }
.cat-c7 { background: #FDF0F4; color: #D9497C; }
.cat-c8 { background: #F0F4E9; color: #6E9B28; }
.cat-c9 { background: $uni-bg-color-muted; color: #5A6270; }
.cat-c10 { background: #FFF5E8; color: #C8821A; }

/* ===== Banner（深底渐变 + 金色标签/按钮 + 篮子插画） ===== */
.bn {
  position: relative;
  height: 236rpx;
  width: calc(100% - 48rpx);
  margin: 20rpx 24rpx 0;
  border-radius: $uni-border-radius-base;
  overflow: hidden;
}

.bn-slide {
  position: relative;
  width: 100%;
  height: 236rpx;
}

.bn-image {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 236rpx;
}

.bn-ph {
  position: relative;
  height: 236rpx;
  background: linear-gradient(100deg, #1C1F24 0%, #3A2220 55%, #7A2A22 100%);
}

.bn-tag {
  position: absolute;
  left: 28rpx;
  top: 28rpx;
  font-size: 20rpx;
  line-height: 28rpx;
  color: #FFD9B0;
  border: 2rpx solid rgba(255, 217, 176, 0.5);
  padding: 0 12rpx;
  letter-spacing: 2rpx;
}

.bn-title {
  position: absolute;
  left: 28rpx;
  top: 76rpx;
  font-size: 40rpx;
  line-height: 48rpx;
  font-weight: 600;
  color: $uni-text-color-inverse;
  letter-spacing: 2rpx;
}

.bn-sub {
  position: absolute;
  left: 28rpx;
  top: 136rpx;
  font-size: 22rpx;
  line-height: 32rpx;
  color: rgba(255, 255, 255, 0.62);
}

.bn-cta {
  position: absolute;
  left: 28rpx;
  top: 180rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: $uni-text-color;
  background: #FFD86B;
  padding: 8rpx 24rpx;
  border-radius: 22rpx;
}

:deep(.bn-art) {
  position: absolute;
  right: 32rpx;
  top: 44rpx;
  font-size: 164rpx;
  width: 164rpx;
  height: 164rpx;
  opacity: 0.9;
  color: rgba(255, 216, 107, 0.85);
}

/* ===== 限时秒杀 ===== */
.blk {
  background: $uni-bg-color;
  margin: 20rpx 24rpx;
  border-radius: $uni-border-radius-base;
  padding: 24rpx;
  box-shadow: $uni-shadow-sm;
}

.bhd {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

:deep(.bhd-icon) {
  color: $uni-color-cart;
  align-self: center;
}

.bhd-title {
  font-size: 32rpx;
  font-weight: 600;
  color: $uni-color-primary;
}

.cd {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6rpx;
  font-size: 22rpx;
  color: $uni-text-color-grey;
}

.cd-num {
  font-family: $uni-font-family-num;
  background: #3A3D42;
  color: $uni-text-color-inverse;
  font-size: 22rpx;
  font-weight: 600;
  padding: 2rpx 6rpx;
  border-radius: 6rpx;
  min-width: 32rpx;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.cd-sep {
  font-family: $uni-font-family-num;
}

.sk-scroll {
  white-space: nowrap;
}

.sk-inner {
  display: inline-flex;
  gap: 16rpx;
}

.sk-item {
  width: 176rpx;
  flex-shrink: 0;
}

.sk-thumb {
  position: relative;
  width: 176rpx;
  height: 176rpx;
  border-radius: 12rpx;
  overflow: hidden;
  background: $uni-bg-color-img;
}

.sk-img {
  width: 176rpx;
  height: 176rpx;
}

.sk-ribbon {
  position: absolute;
  left: 0;
  top: 0;
  font-size: 18rpx;
  line-height: 1;
  padding: 4rpx 10rpx;
  border-radius: 8rpx 0 8rpx 0;
  background: $uni-color-primary;
  color: $uni-text-color-inverse;
  font-weight: 500;
}

.sk-name {
  display: block;
  font-size: 22rpx;
  color: $uni-text-color-grey;
  margin: 12rpx 0 8rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sk-price {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  margin-bottom: 8rpx;
}

.p-symbol {
  font-size: 20rpx;
  font-weight: 700;
  color: $uni-color-price;
  font-style: normal;
}

.p-int {
  font-size: 30rpx;
  font-weight: 700;
  color: $uni-color-price;
  line-height: 1;
}

.p-origin {
  font-size: 20rpx;
  color: $uni-text-color-3;
  text-decoration: line-through;
  margin-left: 4rpx;
}

/* 已抢进度条：文案压在渐变填充内 */
.bar {
  position: relative;
  height: 28rpx;
  border-radius: 14rpx;
  background: $uni-color-primary-50;
  overflow: hidden;
}

.bar-fill {
  height: 28rpx;
  border-radius: 14rpx;
  background: linear-gradient(90deg, $uni-color-price-light, $uni-color-price);
}

.bar-txt {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  padding-left: 12rpx;
  display: flex;
  align-items: center;
  font-size: 18rpx;
  font-weight: 600;
  color: $uni-text-color-inverse;
  font-family: $uni-font-family-num;
}

/* ===== 服务承诺条 ===== */
.svc {
  display: flex;
  background: $uni-bg-color;
  margin: 20rpx 24rpx;
  border-radius: $uni-border-radius-base;
  padding: 20rpx 0;
  box-shadow: $uni-shadow-sm;
}

.svc-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 22rpx;
  color: $uni-text-color-grey;
  border-right: 1rpx solid $uni-border-color-divider;
}

.svc-item:last-child {
  border-right: none;
}

:deep(.svc-icon) {
  color: $uni-color-service;
}

/* ===== 推荐流 ===== */
.sec-t {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 32rpx 24rpx 16rpx;
}

.sec-t-bar {
  width: 6rpx;
  height: 28rpx;
  background: $uni-color-primary;
  border-radius: 4rpx;
}

.sec-t-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.sec-t-hint {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
}

.flow {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  padding: 0 24rpx;
}

.flow-skeleton {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.skeleton-card {
  background: $uni-bg-color;
  border-radius: $uni-border-radius-base;
  overflow: hidden;
}

.skeleton-lines {
  padding: 16rpx;
}

.flow-empty {
  grid-column: 1 / -1;
  display: flex;
  justify-content: center;
  padding: 80rpx 0;
  font-size: 26rpx;
  color: $uni-text-color-placeholder;
}

/* 双列卡片由 ProductCard 按参考稿 .pd 规格渲染，此处不再覆写 */
</style>
