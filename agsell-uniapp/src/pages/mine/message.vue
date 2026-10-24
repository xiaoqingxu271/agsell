<template>
  <view class="page">
    <NavBar title="消息中心" show-back @back="onBack" />

    <scroll-view class="list" scroll-y>
      <view v-if="messages.length" class="cards">
        <view
          v-for="m in messages"
          :key="m.id"
          class="card"
          :class="{ unread: !m.isRead }"
          @click="onMessageTap(m)"
          role="button"
          :aria-label="m.title"
        >
          <view class="ic" :class="`ic-${m.type}`">
            <wd-icon :name="m.type === 'after_sales' ? 'service' : 'bags'" size="32rpx" custom-class="ic-inner" />
          </view>
          <view class="body">
            <view class="row-1">
              <text class="title">{{ m.title }}</text>
              <text v-if="!m.isRead" class="dot" aria-label="未读"></text>
            </view>
            <text class="content">{{ m.content }}</text>
            <text class="time">{{ formatTime(m.createTime) }}</text>
          </view>
        </view>
      </view>

      <view v-else-if="!loading" class="empty">
        <wd-icon name="notification" size="88rpx" custom-class="empty-icon" />
        <text class="empty-t">暂无消息</text>
        <text class="empty-s">订单发货、售后处理结果会在这里通知你</text>
      </view>
      <view class="bottom-safe"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getMessages, markAllRead } from '../../api/message'

const messages = ref([])
const loading = ref(true)

onShow(async () => {
  await loadMessages()
})

onPullDownRefresh(async () => {
  await loadMessages()
  uni.stopPullDownRefresh()
})

async function loadMessages() {
  loading.value = true
  try {
    const res = await getMessages(1, 50)
    if (res.code === 0) {
      messages.value = res.data?.records || []
      // 进页面即算已读：铃铛红点随之下次同步时消失
      if (messages.value.some((m) => !m.isRead)) {
        await markAllRead()
        messages.value = messages.value.map((m) => ({ ...m, isRead: 1 }))
      }
    }
  } catch (e) {
    console.warn('[Message] 消息加载失败', e)
  } finally {
    loading.value = false
  }
}

/** 点消息跳对应单据：订单消息进订单列表（单号在此页不展开详情），售后消息进售后列表 */
function onMessageTap(m) {
  if (!m.relatedNo) return
  if (m.type === 'after_sales') {
    uni.navigateTo({ url: '/pages/after-sales/list/list' })
  } else {
    uni.navigateTo({ url: '/pages/order/list/list' })
  }
}

function formatTime(time) {
  if (!time) return ''
  return String(time).replace('T', ' ').substring(0, 16)
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

.cards {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.card {
  display: flex;
  gap: 20rpx;
  padding: 24rpx;
  background: $uni-bg-color;
  border-radius: 16rpx;
  box-shadow: $uni-shadow-sm;
}

.card.unread {
  border: 2rpx solid $uni-color-primary-100;
}

.ic {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: $uni-color-primary-100;
}

.ic-after_sales {
  background: rgba(255, 150, 40, 0.16);
}

:deep(.ic-inner) {
  color: $uni-color-primary;
}

.body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.row-1 {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.title {
  font-size: 28rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: $uni-color-primary;
  flex-shrink: 0;
}

.content {
  font-size: 24rpx;
  line-height: 36rpx;
  color: $uni-text-color-grey;
  word-break: break-word;
}

.time {
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
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
