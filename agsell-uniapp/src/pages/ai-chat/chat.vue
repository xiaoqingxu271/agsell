<template>
  <view class="chat-page">
    <!-- 问题④修复：NavBar emit('back') 是事件，须用 @back 监听；:back 是 prop 绑定，无效 -->
    <NavBar title="AI 客服" show-back @back="onBack">
      <template #right>
        <view class="nav-online">
          <text class="nav-online-dot"></text>
          <text class="nav-online-t">在线</text>
        </view>
      </template>
    </NavBar>

    <!-- 消息列表（首屏即含欢迎消息：作为第一条 AI 消息保存，带客服头像） -->
    <scroll-view class="msg-list" scroll-y :scroll-into-view="scrollInto" scroll-with-animation>
      <text class="msg-date">{{ sessionTimeLabel }}</text>
      <view
        v-for="(msg, index) in messages"
        :key="index"
        class="msg-row"
        :class="msg.role === 'user' ? 'row-user' : 'row-ai'"
      >
        <!-- 问题①修复：双侧头像（用户=自己头像，客服=品牌红图标块） -->
        <view v-if="msg.role === 'ai'" class="avatar avatar-ai">
          <wd-icon name="service" size="34rpx" custom-class="av-ai-icon" />
        </view>
        <view class="msg-main">
          <view class="bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'">
            <!-- 流式回复：首 token 前打字动画，之后逐字渲染 + 闪烁光标（内部阶段文案不向用户展示） -->
            <view v-if="msg.streaming && !msg.content" class="typing-dots">
              <view class="dot"></view>
              <view class="dot"></view>
              <view class="dot"></view>
            </view>
            <text v-else class="bubble-text" user-select>{{ msg.content }}</text>
            <text v-if="msg.streaming && msg.content" class="stream-cursor">▍</text>
          </view>

          <!-- 问题③：客服回答后的下一轮询问小贴士（气泡外 chips） -->
          <view v-if="msg.suggestions && msg.suggestions.length" class="suggest-wrap">
            <view
              v-for="s in msg.suggestions"
              :key="s"
              class="suggest-item"
              @click="sendQuick(s)"
              role="button"
            >
              <text class="suggest-text">{{ s }}</text>
            </view>
          </view>
        </view>
        <image
          v-if="msg.role === 'user'"
          class="avatar avatar-user"
          :src="userAvatar"
          mode="aspectFill"
        />
      </view>

      <!-- 滚动锚点 -->
      <view id="bottom-anchor" class="anchor"></view>
    </scroll-view>

    <!-- 底部输入栏 -->
    <view class="input-bar">
      <input
        v-model="draft"
        class="chat-input"
        placeholder="问问 AI 客服，比如「送礼选什么」"
        placeholder-class="input-placeholder"
        confirm-type="send"
        :disabled="sending"
        @confirm="onSend"
      />
      <button class="send-btn" :disabled="sending || !draft.trim()" @click="onSend">
        {{ sending ? '…' : '发送' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { sendAiChatStream } from '../../api/ai'
import { isLoggedIn } from '../../utils/request'

const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scrollInto = ref('')
const sessionId = ref('')
const sessionTimeLabel = ref('')

const quickQuestions = [
  '怎么申请退款？',
  '运费怎么算的？',
  '我的订单现在是什么状态？',
  '我申请过退款吗？结果怎么样？'
]

// 问题①：用户头像取自登录 userInfo（wxLogin 时写入 { userId, nickname, avatar, phone }），未登录用默认头像
const userAvatar = computed(() => {
  try {
    const info = uni.getStorageSync('userInfo')
    if (info && info.avatar) return info.avatar
  } catch (e) {
    /* ignore */
  }
  return '/static/default-avatar.png'
})

onLoad(() => {
  // 会话 ID：页面级生成，多轮对话共用（服务端记忆以 sessionId 为 key）
  sessionId.value = 'wx_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
  const now = new Date()
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  sessionTimeLabel.value = `今天 ${p(now.getHours())}:${p(now.getMinutes())} · 会话已开始`
  // 首条欢迎消息：作为正式 AI 消息保存进历史（带客服头像），快捷问题作为下一轮小贴士
  messages.value.push({
    role: 'ai',
    content: '您好，我是 agsell AI 客服助手\n可以问我订单、物流、售后、平台规则等问题～',
    suggestions: [...quickQuestions]
  })
})

function onBack() {
  // 从"我的"页 navigateTo 进入，delta=1 返回上一页
  uni.navigateBack({ delta: 1 })
}

async function sendQuick(q) {
  draft.value = q
  await onSend()
}

function pushMessage(role, content, suggestions) {
  // 问题②：Agnes 回复常带前导换行，trim 后避免气泡顶部空白
  messages.value.push({ role, content: (content || '').trim(), suggestions })
  scrollToBottom()
}

function scrollToBottom() {
  // 滚动修复：scroll-into-view 只有值变化才触发。
  // 连续滚动时直接赋 'bottom-anchor' 值相同，Vue 不触发更新 → 必须"先清空→下一帧再设置"。
  nextTick(() => {
    scrollInto.value = ''
    nextTick(() => {
      scrollInto.value = 'bottom-anchor'
    })
  })
}

async function onSend() {
  const text = (draft.value || '').trim()
  if (!text || sending.value) return
  draft.value = ''
  pushMessage('user', text)

  sending.value = true
  // 预插入 AI 气泡：首 token 前显示打字动画，token 到达后逐字渲染
  messages.value.push({ role: 'ai', content: '', suggestions: null, streaming: true })
  const msg = messages.value[messages.value.length - 1]

  try {
    await sendAiChatStream(sessionId.value, text, {
      onToken: (t) => {
        // Agnes 首包常带前导换行，流式期间就剥掉，避免气泡顶部出现空行
        msg.content = msg.content ? msg.content + t : (t || '').replace(/^\s+/, '')
        scrollToBottom()
      },
      onStatus: () => {
        // 内部阶段事件（检索/业务查询中）不向用户展示，仅保持流解析完整
        scrollToBottom()
      },
      onDone: (d) => {
        msg.streaming = false
        // 兜底：流正常收尾但无内容（不应发生，防御性处理）
        msg.content = (msg.content || '').trim() || '抱歉，服务开小差了，请稍后再试。'
        if (d && d.suggestions && d.suggestions.length) {
          msg.suggestions = d.suggestions
        }
        scrollToBottom()
      },
      onError: (e) => {
        // 上游 error 事件：展示服务端文案；气泡已有内容时保留已生成的部分
        msg.streaming = false
        if (!msg.content) {
          msg.content = (e && e.message) || '抱歉，服务开小差了，请稍后再试。'
        }
        scrollToBottom()
      }
    })
  } catch (e) {
    console.warn('AI 客服流式请求失败', e)
    // 传输失败 / 业务失败（token 失效 40100 等）
    msg.streaming = false
    if (!msg.content) {
      if (e?.code === 40100 || e?.code === 10001) {
        msg.content = '登录状态已失效，请重新登录后再试。'
      } else {
        msg.content = '网络异常，请检查网络后重试。'
      }
    }
  } finally {
    sending.value = false
    scrollToBottom()
  }
}
</script>

<style lang="scss" scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: $uni-bg-color-grey;
  overflow: hidden;
}

/* 消息列表 */
.msg-list {
  flex: 1;
  padding: 24rpx;
  box-sizing: border-box;
  height: 0;
  background: #F6F7F9;
}

.msg-date {
  display: block;
  text-align: center;
  padding: 4rpx 0 24rpx;
  font-size: 20rpx;
  color: $uni-text-color-disable;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.row-user {
  justify-content: flex-end;
}

.row-ai {
  justify-content: flex-start;
}

.msg-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 524rpx;
  min-width: 0;
}

.row-user .msg-main {
  align-items: flex-end;
}

/* 头像 */
.avatar {
  width: 60rpx;
  height: 60rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}

.avatar-ai {
  background: $uni-color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.av-ai-icon) {
  color: $uni-text-color-inverse;
}

.avatar-user {
  border-radius: 12rpx;
  background: $uni-bg-color-img;
}

/* 气泡 */
.bubble {
  max-width: 100%;
  padding: 18rpx 22rpx;
  border-radius: 20rpx;
  font-size: 26rpx;
  line-height: 40rpx;
  word-break: break-word;
}

.bubble-user {
  background: #C9E7FF;
  color: $uni-text-color;
  border-top-right-radius: 4rpx;
}

.bubble-ai {
  background: $uni-bg-color;
  color: $uni-text-color;
  border-top-left-radius: 4rpx;
  box-shadow: $uni-shadow-sm;
}

/* 下一轮询问小贴士（气泡外 chips） */
.suggest-wrap {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.suggest-item {
  height: 56rpx;
  display: flex;
  align-items: center;
  background: $uni-bg-color;
  border: 2rpx solid $uni-border-color;
  border-radius: 28rpx;
  padding: 0 22rpx;
}

.suggest-item:active {
  background: $uni-bg-color-hover;
}

.suggest-text {
  font-size: 22rpx;
  color: $uni-text-color;
}

/* 打字动画 */
.typing-dots {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 0;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: $uni-border-radius-circle;
  background: $uni-text-color-placeholder;
  animation: blink 1.2s infinite ease-in-out;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; }
  40% { opacity: 1; }
}

/* 流式输出光标 */
.stream-cursor {
  color: $uni-color-primary;
  animation: cursor-blink 0.8s step-end infinite;
}

@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 滚动锚点 */
.anchor {
  height: 1rpx;
}

/* 导航右侧在线状态 */
.nav-online {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.nav-online-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: $uni-color-service;
}

.nav-online-t {
  font-size: 22rpx;
  color: $uni-color-service;
}

/* 底部输入栏 */
.input-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 98rpx;
  padding: 0 20rpx;
  padding-bottom: calc(18rpx + env(safe-area-inset-bottom));
  box-sizing: content-box;
  background: $uni-bg-color;
  border-top: 2rpx solid $uni-border-color-divider;
}

.chat-input {
  flex: 1;
  height: 68rpx;
  background: $uni-bg-color-muted;
  border: 2rpx solid $uni-border-color;
  border-radius: 34rpx;
  padding: 0 24rpx;
  font-size: 24rpx;
  color: $uni-text-color;
}

.input-placeholder {
  color: $uni-text-color-placeholder;
}

.send-btn {
  flex-shrink: 0;
  width: 112rpx;
  height: 60rpx;
  line-height: 60rpx;
  padding: 0;
  background: $uni-color-primary;
  color: $uni-text-color-inverse;
  font-size: 24rpx;
  font-weight: 600;
  border-radius: 30rpx;
  border: none;
  margin: 0;
}

.send-btn::after {
  border: none;
}

.send-btn[disabled] {
  background: $uni-text-color-disable;
  color: $uni-text-color-inverse;
}
</style>
