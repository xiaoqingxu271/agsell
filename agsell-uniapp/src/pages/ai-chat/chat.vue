<template>
  <view class="chat-page">
    <!-- 问题④修复：NavBar emit('back') 是事件，须用 @back 监听；:back 是 prop 绑定，无效 -->
    <NavBar title="AI 客服" show-back @back="onBack" />

    <!-- 消息列表（首屏即含欢迎消息：作为第一条 AI 消息保存，带客服头像） -->
    <scroll-view class="msg-list" scroll-y :scroll-into-view="scrollInto" scroll-with-animation>
      <view
        v-for="(msg, index) in messages"
        :key="index"
        class="msg-row"
        :class="msg.role === 'user' ? 'row-user' : 'row-ai'"
      >
        <!-- 问题①修复：双侧头像（用户=自己头像，客服=默认客服头像） -->
        <image
          v-if="msg.role === 'ai'"
          class="avatar avatar-ai"
          src="/static/icon-service-avatar.png"
          mode="aspectFill"
        />
        <view class="bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'">
          <text class="bubble-text" user-select>{{ msg.content }}</text>

          <!-- 问题③：客服回答后的下一轮询问小贴士 -->
          <view v-if="msg.role === 'ai' && msg.suggestions && msg.suggestions.length" class="suggest-wrap">
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

      <!-- 输入中 -->
      <view v-if="sending" class="msg-row row-ai">
        <image class="avatar avatar-ai" src="/static/icon-service-avatar.png" mode="aspectFill" />
        <view class="bubble bubble-ai typing">
          <view class="dot"></view>
          <view class="dot"></view>
          <view class="dot"></view>
        </view>
      </view>

      <!-- 滚动锚点 -->
      <view id="bottom-anchor" class="anchor"></view>
    </scroll-view>

    <!-- 底部输入栏 -->
    <view class="input-bar">
      <input
        v-model="draft"
        class="chat-input"
        placeholder="请输入您的问题…"
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
import { sendAiChat } from '../../api/ai'
import { isLoggedIn } from '../../utils/request'

const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scrollInto = ref('')
const sessionId = ref('')

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
  try {
    const res = await sendAiChat(sessionId.value, text)
    if (res.code === 0 && res.data?.reply) {
      // 问题③：透传服务端按意图生成的下轮建议问题
      pushMessage('ai', res.data.reply, res.data.suggestions)
    } else {
      pushMessage('ai', '抱歉，服务开小差了，请稍后再试。')
    }
  } catch (e) {
    console.warn('AI 客服请求失败', e)
    // Token 失效等业务失败：提示重新登录；网络失败：友好提示
    if (e?.code === 40100 || e?.code === 10001) {
      pushMessage('ai', '登录状态已失效，请重新登录后再试。')
    } else {
      pushMessage('ai', '网络异常，请检查网络后重试。')
    }
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #F4F6F5;
  overflow: hidden;
}

/* 消息列表 */
.msg-list {
  flex: 1;
  padding: 24rpx 32rpx;
  box-sizing: border-box;
  height: 0;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 24rpx;
}

.row-user {
  justify-content: flex-end;
}

.row-ai {
  justify-content: flex-start;
}

/* 问题①：头像 */
.avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: #E5E7EB;
}

.avatar-ai {
  margin-right: 16rpx;
}

.avatar-user {
  margin-left: 16rpx;
}

.bubble {
  max-width: 78%;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  font-size: 28rpx;
  line-height: 1.6;
  word-break: break-word;
}

.bubble-user {
  background: #15803D;
  color: #FFFFFF;
  border-top-right-radius: 4rpx;
}

.bubble-ai {
  background: #FFFFFF;
  color: #1F2937;
  border: 1rpx solid #E3E7E5;
  border-top-left-radius: 4rpx;
}

/* 问题③：下一轮询问小贴士 */
.suggest-wrap {
  margin-top: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  border-top: 1rpx dashed #E3E7E5;
  padding-top: 16rpx;
}

.suggest-item {
  align-self: flex-start;
  background: #F0FDF4;
  border: 1rpx solid #BBF7D0;
  border-radius: 12rpx;
  padding: 12rpx 20rpx;
}

.suggest-item:active {
  background: #DCFCE7;
}

.suggest-text {
  font-size: 24rpx;
  color: #166534;
}

/* 输入中动画 */
.typing {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #9CA3AF;
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

/* 滚动锚点 */
.anchor {
  height: 1rpx;
}

/* 底部输入栏 */
.input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #E3E7E5;
}

.chat-input {
  flex: 1;
  height: 80rpx;
  background: #F4F6F5;
  border-radius: 40rpx;
  padding: 0 32rpx;
  font-size: 28rpx;
  color: #1F2937;
}

.input-placeholder {
  color: #9CA3AF;
}

.send-btn {
  flex-shrink: 0;
  height: 80rpx;
  line-height: 80rpx;
  padding: 0 40rpx;
  background: #15803D;
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: 600;
  border-radius: 40rpx;
  border: none;
  margin: 0;
}

.send-btn::after {
  border: none;
}

.send-btn[disabled] {
  background: #A7CBB5;
  color: #FFFFFF;
}
</style>
