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

    <!-- 从商详页进线：明确告诉用户"这个"指的是哪件，并能一键取消该上下文 -->
    <view v-if="contextProduct" class="ctx-bar">
      <image class="ctx-img" :src="contextProduct.mainImage || '/static/default-product.png'" mode="aspectFill" />
      <text class="ctx-label">正在咨询</text>
      <text class="ctx-name">{{ contextProduct.name }}</text>
      <text class="ctx-clear" @click="clearContext" role="button" aria-label="取消咨询该商品">✕</text>
    </view>

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

          <!-- 知识依据：服务端按检索命中结构化下发（id+问句），不是模型自己念的。
               反幻觉的证据链可视化：这条回答"有出处"，点开能对上知识库条目。 -->
          <view v-if="msg.references && msg.references.length" class="ref-list">
            <view v-for="r in msg.references" :key="r.id" class="ref-item">
              <text class="ref-tag">依据</text>
              <text class="ref-q">{{ r.question }}</text>
            </view>
          </view>

          <!-- 导购推荐结果：可点击商品卡（图片/价格/产地/口碑），点卡进详情、点＋直接加购。
               没有卡片时用户看完推荐还得自己再去搜一遍，导购链路在 UI 上等于断掉。 -->
          <view v-if="msg.items && msg.items.length" class="rec-list">
            <view
              v-for="p in msg.items"
              :key="p.id"
              class="rec-card"
              hover-class="rec-card-hover"
              :hover-stay-time="120"
              @click="goProduct(p)"
              role="button"
              :aria-label="'查看 ' + p.name"
            >
              <image class="rec-img" :src="p.mainImage || '/static/default-product.png'" mode="aspectFill" lazy-load />
              <view class="rec-in">
                <text class="rec-nm">{{ p.name }}</text>
                <view class="rec-tags">
                  <text v-if="p.origin" class="rec-tag tg">{{ p.origin }}</text>
                  <!-- 口碑只在确有评价时展示，零评价商品不谎称"好评率高" -->
                  <text v-if="p.reviewCount && p.positiveRate" class="rec-tag tr">{{ p.positiveRate }}% 好评</text>
                  <text v-for="(k, i) in (p.topKeywords || []).slice(0, 2)" :key="i" class="rec-tag tk">{{ k }}</text>
                </view>
                <view class="rec-prow">
                  <view class="rec-p">
                    <text class="rec-p-i">¥</text>
                    <text class="rec-p-b">{{ p.price }}</text>
                  </view>
                  <view class="rec-add" @click.stop="quickAdd(p)" role="button" aria-label="加入购物车">
                    <wd-icon name="add" size="24rpx" custom-class="rec-add-ic" />
                  </view>
                </view>
                <text class="rec-sold">已售 {{ p.sales || 0 }}</text>
              </view>
            </view>
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
import { getProductDetail } from '../../api/product'
import { addToCart } from '../../api/cart'
import { isLoggedIn, wxLogin } from '../../utils/request'

const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scrollInto = ref('')
const sessionId = ref('')
const sessionTimeLabel = ref('')

// 商详页进线时携带的商品上下文：整个会话期间随每条消息一起发给服务端，
// 用户问"这个能放多久/适合送人吗"时才有明确指代对象
const contextProduct = ref(null)

// 会话号按登录身份持久化：服务端多轮记忆以 (userId, sessionId) 为 thread key，
// 每次进页重随机会让历史 thread 永远命中不到，等于没有会话记忆
const SESSION_KEY = 'aiChatSession'

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

onLoad((options) => {
  // 会话 ID：同一登录用户复用上次会话（服务端记忆以 userId+sessionId 为键）
  sessionId.value = resolveSessionId()
  const now = new Date()
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  sessionTimeLabel.value = `今天 ${p(now.getHours())}:${p(now.getMinutes())} · 会话已开始`
  // 首条欢迎消息：作为正式 AI 消息保存进历史（带客服头像），快捷问题作为下一轮小贴士
  messages.value.push({
    role: 'ai',
    content: '您好，我是 agsell AI 客服助手\n可以问我订单、物流、售后、平台规则等问题～',
    suggestions: [...quickQuestions]
  })
  // 从商品详情页进来：先立起"正在咨询"条，再给一条该商品的快捷问题
  const pid = String(options?.productId || '')
  if (pid && pid !== 'undefined' && pid !== 'null') loadContextProduct(pid)
})

/**
 * 取当前咨询商品（仅需登录外的公开详情接口）。
 * 失败/下架一律静默忽略——聊天本身照常可用，绝不能因为上下文没取到就给用户报错。
 */
async function loadContextProduct(pid) {
  try {
    const res = await getProductDetail(pid)
    if (res && res.code === 0 && res.data) {
      contextProduct.value = {
        id: res.data.id,
        name: res.data.name,
        mainImage: res.data.mainImage
      }
      const first = messages.value[0]
      if (first && Array.isArray(first.suggestions)) {
        first.suggestions = ['这个商品怎么样？', ...first.suggestions]
      }
    }
  } catch (e) {
    console.warn('商品上下文加载失败，按普通咨询继续', e)
  }
}

function clearContext() {
  contextProduct.value = null
}

/** 卡片点击：进对应商品详情页（与服务端下发的 id 一致，不自己拼链接格式） */
function goProduct(p) {
  if (!p || !p.id) return
  uni.navigateTo({ url: `/pages/product/product?id=${p.id}` })
}

/** 卡片加购：与首页商卡同一接口，规格由后端取默认；未登录先静默登录 */
async function quickAdd(p) {
  if (!p || !p.id) return
  try {
    if (!isLoggedIn()) {
      const ok = await wxLogin()
      if (!ok) return
    }
    const res = await addToCart({ productId: p.id, specId: null, quantity: 1 })
    uni.showToast({
      title: res && res.code === 0 ? '已加入购物车' : (res && res.message) || '加购失败',
      icon: res && res.code === 0 ? 'success' : 'none'
    })
  } catch (e) {
    // 加购是聊天里的附加动作：网络/服务异常只提示，不打断对话
    uni.showToast({ title: '网络异常，请稍后再试', icon: 'none' })
  }
}

/** 当前登录身份键：换账号或登出后旧会话作废（绝不复用别人的 thread） */
function currentOwnerKey() {
  if (!isLoggedIn()) return 'anon'
  try {
    const info = uni.getStorageSync('userInfo')
    if (info && info.userId !== undefined && info.userId !== null) return `u_${info.userId}`
  } catch (e) {
    /* ignore */
  }
  return 'u_unknown'
}

function resolveSessionId() {
  const owner = currentOwnerKey()
  try {
    const saved = uni.getStorageSync(SESSION_KEY)
    if (saved && saved.owner === owner && saved.sessionId) return saved.sessionId
  } catch (e) {
    /* ignore */
  }
  const sid = 'wx_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
  try {
    uni.setStorageSync(SESSION_KEY, { owner, sessionId: sid })
  } catch (e) {
    /* ignore */
  }
  return sid
}

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
  messages.value.push({ role: 'ai', content: '', suggestions: null, items: null, streaming: true })
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
      // 候选一确定就渲染卡片，用户不用等 LLM 把话说完才能点进商品
      onItems: (items) => {
        if (items && items.length) {
          msg.items = items
          scrollToBottom()
        }
      },
      onDone: (d) => {
        msg.streaming = false
        // 兜底：流正常收尾但无内容（不应发生，防御性处理）
        msg.content = (msg.content || '').trim() || '抱歉，服务开小差了，请稍后再试。'
        if (d && d.suggestions && d.suggestions.length) {
          msg.suggestions = d.suggestions
        }
        // items 事件若因中途断流没收到，done 里还有一份
        if (!msg.items && d && d.items && d.items.length) msg.items = d.items
        // 知识依据随 done 下发，下一轮提问前一直挂在气泡下
        if (d && d.references && d.references.length) msg.references = d.references
        scrollToBottom()
      },
      onError: (e) => {
        // 上游 error 事件：文案由 api/ai.js 按 code 查白名单得到（不含服务端异常细节）；
        // 气泡已有内容时保留已生成的部分
        msg.streaming = false
        if (!msg.content) {
          msg.content = (e && e.message) || '抱歉，服务开小差了，请稍后再试。'
        }
        scrollToBottom()
      }
    }, contextProduct.value ? contextProduct.value.id : null)
  } catch (e) {
    console.warn('AI 客服流式请求失败', e)
    // 传输失败 / 业务失败（token 失效 40100 等）
    msg.streaming = false
    if (!msg.content) {
      if (e?.code === 40100 || e?.code === 10001) {
        msg.content = '登录状态已失效，请重新登录后再试。'
      } else if (e?.code === 42900) {
        // 网关限流：请求没进 AI 服务，稍后再发即可，历史上下文不受影响
        msg.content = '发送有点频繁，请稍后再试。'
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

/* 商详页咨询上下文条（品质电商风：细边框 + 缩略图，不抢消息区视觉） */
.ctx-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 14rpx 24rpx;
  background: $uni-bg-color;
  border-bottom: 2rpx solid $uni-border-color-divider;
}

.ctx-img {
  width: 48rpx;
  height: 48rpx;
  border-radius: 8rpx;
  background: $uni-bg-color-img;
  flex-shrink: 0;
}

.ctx-label {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
  flex-shrink: 0;
}

.ctx-name {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: $uni-text-color;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ctx-clear {
  font-size: 26rpx;
  color: $uni-text-color-placeholder;
  padding: 0 8rpx;
}

/* 导购推荐商品卡 */
.rec-list {
  width: 100%;
  margin-top: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.rec-card {
  display: flex;
  gap: 16rpx;
  padding: 16rpx;
  background: $uni-bg-color;
  border: 2rpx solid $uni-border-color;
  border-radius: 16rpx;
  box-shadow: $uni-shadow-sm;
}

.rec-card-hover {
  background: $uni-bg-color-hover;
}

.rec-img {
  width: 128rpx;
  height: 128rpx;
  border-radius: 12rpx;
  background: $uni-bg-color-img;
  flex-shrink: 0;
}

.rec-in {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.rec-nm {
  font-size: 26rpx;
  line-height: 36rpx;
  color: $uni-text-color;
  font-weight: 600;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.rec-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

/* 标签配色沿用首页商卡同一套 token（描边式，不另造一套视觉语言）：
   产地/服务=服务绿，好评率=品牌红，口碑词=灰 */
.rec-tag {
  font-size: 20rpx;
  line-height: 28rpx;
  padding: 0 8rpx;
  border: 2rpx solid $uni-border-color;
  border-radius: 6rpx;
}

.rec-tag.tg {
  color: $uni-color-service;
  border-color: $uni-color-service-100;
}

.rec-tag.tr {
  color: $uni-color-primary;
  border-color: $uni-color-primary-100;
}

.rec-tag.tk {
  color: $uni-text-color-grey;
  border-color: $uni-border-color;
}

.rec-prow {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12rpx;
}

.rec-p {
  display: flex;
  align-items: baseline;
  color: $uni-color-primary;
}

.rec-p-i {
  font-size: 22rpx;
  font-weight: 600;
}

.rec-p-b {
  font-size: 34rpx;
  font-weight: 700;
}

.rec-add {
  width: 48rpx;
  height: 48rpx;
  border-radius: 10rpx;
  background: $uni-color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

:deep(.rec-add-ic) {
  color: $uni-text-color-inverse;
}

.rec-sold {
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
}

/* 知识依据标签（气泡外，紧贴气泡）：描边条 + 品牌色"依据"前缀 */
.ref-list {
  width: 100%;
  margin-top: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.ref-item {
  display: flex;
  align-items: center;
  gap: 10rpx;
  max-width: 100%;
}

.ref-tag {
  flex-shrink: 0;
  font-size: 18rpx;
  line-height: 26rpx;
  padding: 0 8rpx;
  border: 2rpx solid $uni-color-primary-100;
  border-radius: 6rpx;
  color: $uni-color-primary;
}

.ref-q {
  font-size: 20rpx;
  color: $uni-text-color-grey;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
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
