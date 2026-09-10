<template>
  <view class="as-list-page">
    <NavBar title="我的售后" @back="uni.navigateBack()" />

    <!-- 状态筛选 -->
    <view class="filter-bar">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="filter-item"
        :class="{ 'filter-active': activeStatus === tab.value }"
        @click="switchTab(tab.value)"
        role="button"
      >{{ tab.label }}</view>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 空态 -->
      <view v-if="!loading && list.length === 0" class="empty-state">
        <text class="empty-text">暂无售后记录</text>
      </view>

      <!-- 售后列表 -->
      <view
        v-for="item in list"
        :key="item.id"
        class="as-card card"
        @click="goDetail(item.afterSalesNo)"
        role="button"
      >
        <view class="as-card-header">
          <text class="as-no">{{ item.afterSalesNo }}</text>
          <text class="as-status">{{ item.statusText }}</text>
        </view>
        <view class="as-card-body">
          <view class="as-row">
            <text class="as-label">订单号</text>
            <text class="as-value">{{ item.orderNo }}</text>
          </view>
          <view class="as-row">
            <text class="as-label">售后类型</text>
            <text class="as-value">{{ item.typeText }} · {{ item.reasonTypeText }}</text>
          </view>
          <view class="as-row">
            <text class="as-label">退款金额</text>
            <text class="as-value pay">¥{{ item.refundAmount }}</text>
          </view>
          <view class="as-row">
            <text class="as-label">申请时间</text>
            <text class="as-value">{{ formatDate(item.createTime) }}</text>
          </view>
        </view>
      </view>

      <!-- 加载更多 -->
      <view v-if="hasMore" class="load-more" @click="loadMore" role="button">加载更多</view>
      <view v-else-if="list.length > 0" class="load-more">没有更多了</view>
    </scroll-view>
  </view>
</template><script setup>
import { ref, computed } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import NavBar from '../../../components/NavBar/NavBar.vue'
import { getAfterSalesList } from '../../../api/afterSales'
import { formatDate } from '../../../utils/format'

const tabs = [
  { label: '全部', value: null },
  { label: '待处理', value: 0 },
  { label: '已同意', value: 1 },
  { label: '已拒绝', value: 2 },
  { label: '已撤销', value: 3 }
]

const activeStatus = ref(null)
const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)

const hasMore = computed(() => list.value.length < total.value)

onLoad(() => {
  loadList(true)
})

async function switchTab(status) {
  if (activeStatus.value === status) return
  activeStatus.value = status
  loadList(true)
}

async function loadList(reset) {
  if (loading.value) return
  loading.value = true
  if (reset) {
    pageNum.value = 1
    list.value = []
  }
  const res = await getAfterSalesList({
    pageNum: pageNum.value,
    pageSize,
    status: activeStatus.value
  })
  loading.value = false
  if (res.code === 0) {
    const data = res.data
    total.value = data.total || 0
    list.value = reset ? (data.records || []) : [...list.value, ...(data.records || [])]
  }
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  pageNum.value += 1
  loadList(false)
}

onReachBottom(() => {
  loadMore()
})

function goDetail(afterSalesNo) {
  uni.navigateTo({ url: `/pages/after-sales/detail/detail?afterSalesNo=${afterSalesNo}` })
}
</script>

<style scoped>
.as-list-page {
  min-height: 100vh;
  background: #F4F6F5;
  display: flex;
  flex-direction: column;
}

.filter-bar {
  display: flex;
  background: #FFFFFF;
  border-bottom: 1rpx solid #E3E7E5;
  padding: 0 24rpx;
}

.filter-item {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 28rpx;
  color: #6B7280;
  position: relative;
  min-height: 88rpx;
  line-height: 40rpx;
  box-sizing: border-box;
}

.filter-active {
  color: #15803D;
  font-weight: 600;
}

.filter-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 56rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background: #15803D;
}

.content {
  flex: 1;
  min-height: 0;
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

.as-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #EEF1EF;
}

.as-no {
  font-size: 24rpx;
  color: #6B7280;
}

.as-status {
  font-size: 24rpx;
  font-weight: 600;
  color: #15803D;
}

.as-row {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
  font-size: 26rpx;
}

.as-label { color: #6B7280; flex-shrink: 0; margin-right: 24rpx; }
.as-value { color: #1F2937; text-align: right; word-break: break-all; }
.as-value.pay { color: #A16207; font-weight: 600; font-variant-numeric: tabular-nums; }

.empty-state {
  text-align: center;
  padding: 120rpx 0;
}

.empty-text {
  font-size: 28rpx;
  color: #4B5563;
}

.load-more {
  text-align: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: #6B7280;
}
</style>
