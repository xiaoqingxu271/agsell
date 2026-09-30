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
          <!-- 流式阶段提示（业务查询中：正在查询订单…） -->
          <view v-if="msg.streaming && msg.stage" class="stage-hint">
            <text class="stage-text">{{ msg.stage }}</text>
          </view>
          <!-- 流式回复：首 token 前打字动画，之后逐字渲染 + 闪烁光标 -->
          <view v-if="msg.streaming && !msg.content" class="typing-dots">
            <view class="dot"></view>
            <view class="dot"></view>
            <view class="dot"></view>
          </view>
          <text v-else class="bubble-text" user-select>{{ msg.content }}</text>
          <text v-if="msg.streaming && msg.content" class="stream-cursor">▍</text>

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
import { sendAiChatStream } from '../../api/ai'
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

// 流式阶段提示文案（与 Python 侧 stage 事件对应）
const STAGE_LABELS = {
  faq_retrieve: '正在翻阅知识库…',
  order_query: '正在查询订单…',
  logistics_query: '正在查询物流…',
  after_sales_query: '正在查询售后进度…'
}

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
  // 预插入 AI 气泡：首 token 前显示打字动画，token 到达后逐字渲染
  messages.value.push({ role: 'ai', content: '', suggestions: null, streaming: true, stage: '' })
  const msg = messages.value[messages.value.length - 1]

  try {
    await sendAiChatStream(sessionId.value, text, {
      onToken: (t) => {
        msg.stage = ''
        msg.content += t
        scrollToBottom()
      },
      onStatus: (s) => {
        msg.stage = (s && STAGE_LABELS[s.stage]) || ''
        scrollToBottom()
      },
      onDone: (d) => {
        msg.streaming = false
        msg.stage = ''
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
        msg.stage = ''
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
    msg.stage = ''
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

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #F5F5F5;
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
  background: #00B578;
  color: #FFFFFF;
  border-top-right-radius: 4rpx;
}

.bubble-ai {
  background: #FFFFFF;
  color: #111827;
  border: 1rpx solid #E5E7EB;
  border-top-left-radius: 4rpx;
}

/* 问题③：下一轮询问小贴士 */
.suggest-wrap {
  margin-top: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  border-top: 1rpx dashed #E5E7EB;
  padding-top: 16rpx;
}

.suggest-item {
  align-self: flex-start;
  background: #ECFDF5;
  border: 1rpx solid #D1FAE5;
  border-radius: 12rpx;
  padding: 12rpx 20rpx;
}

.suggest-item:active {
  background: #D1FAE5;
}

.suggest-text {
  font-size: 24rpx;
  color: #00B578;
}

/* 流式阶段提示（业务查询中） */
.stage-hint {
  margin-bottom: 12rpx;
}

.stage-text {
  font-size: 24rpx;
  color: #9CA3AF;
}

/* 流式首 token 前的打字动画（在 AI 气泡内） */
.typing-dots {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 0;
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

/* 流式输出光标 */
.stream-cursor {
  color: #00B578;
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

/* 底部输入栏 */
.input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #E5E7EB;
}

.chat-input {
  flex: 1;
  height: 80rpx;
  background: #F5F5F5;
  border-radius: 40rpx;
  padding: 0 32rpx;
  font-size: 28rpx;
  color: #111827;
}

.input-placeholder {
  color: #9CA3AF;
}

.send-btn {
  flex-shrink: 0;
  height: 80rpx;
  line-height: 80rpx;
  padding: 0 40rpx;
  background: #00B578;
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
