<template>
  <view class="pf-page">
    <NavBar title="编辑资料" @back="uni.navigateBack()" />

    <view class="card">
      <!-- 头像 -->
      <view class="row">
        <text class="row-label">头像</text>
        <!-- #ifdef MP-WEIXIN -->
        <button class="row-avatar" open-type="chooseAvatar" @chooseavatar="onWxAvatar">
          <image v-if="avatarPreview" class="avatar" :src="avatarPreview" mode="aspectFill" />
          <view v-else class="avatar avatar--ph">
            <wd-icon name="user" size="48rpx" custom-class="avatar-ic" />
          </view>
        </button>
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <view class="row-avatar" @click="onLocalAvatar" role="button" aria-label="更换头像">
          <image v-if="avatarPreview" class="avatar" :src="avatarPreview" mode="aspectFill" />
          <view v-else class="avatar avatar--ph">
            <wd-icon name="user" size="48rpx" custom-class="avatar-ic" />
          </view>
        </view>
        <!-- #endif -->
        <text v-if="avatarUploading" class="row-state">上传中…</text>
        <wd-icon name="chevron-right" size="24rpx" custom-class="row-chev" />
      </view>

      <!-- 昵称 -->
      <view class="row">
        <text class="row-label">昵称</text>
        <!-- #ifdef MP-WEIXIN -->
        <input
          v-model="nickname"
          class="row-input"
          type="nickname"
          placeholder="请输入昵称"
          placeholder-style="color:#9AA0A8"
          maxlength="20"
          @blur="onNicknameBlur"
        />
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <input
          v-model="nickname"
          class="row-input"
          placeholder="请输入昵称"
          placeholder-style="color:#9AA0A8"
          maxlength="20"
        />
        <!-- #endif -->
      </view>

      <!-- 手机号 -->
      <view class="row row--last row--tap" @click="openBind" role="button" aria-label="绑定手机号">
        <text class="row-label">手机号</text>
        <text class="row-value">{{ phoneText }}</text>
        <wd-icon name="chevron-right" size="24rpx" custom-class="row-chev" />
      </view>
    </view>

    <view class="save-wrap">
      <view class="save-btn" :class="{ 'is-disabled': avatarUploading }" @click="onSave" role="button">保存</view>
    </view>

    <!-- 手机号绑定抽屉 -->
    <view v-if="showBind" class="mask" @click="closeBind">
      <view class="sheet" @click.stop>
        <view class="sheet-hd">
          <text class="sheet-title">{{ phone ? '换绑手机号' : '绑定手机号' }}</text>
          <view class="sheet-close" @click="closeBind" role="button" aria-label="关闭">
            <wd-icon name="close" size="32rpx" custom-class="sheet-close-ic" />
          </view>
        </view>
        <text class="sheet-sub">手机号用于订单联系与售后核身</text>
        <view class="field">
          <text class="field-label">手机号</text>
          <input
            v-model="bindForm.phone"
            class="field-input"
            type="number"
            maxlength="11"
            placeholder="请输入手机号"
            placeholder-style="color:#9AA0A8"
          />
        </view>
        <view class="field">
          <text class="field-label">验证码</text>
          <view class="code-row">
            <input
              v-model="bindForm.code"
              class="field-input code-input"
              type="number"
              maxlength="6"
              placeholder="6位验证码"
              placeholder-style="color:#9AA0A8"
            />
            <view
              class="code-btn"
              :class="{ 'is-disabled': countdown > 0 || sending }"
              @click="onSendCode"
              role="button"
            >{{ countdown > 0 ? countdown + 's' : (sending ? '发送中' : '获取验证码') }}</view>
          </view>
        </view>
        <view class="sheet-save" @click="onBindSubmit" role="button">{{ phone ? '确认换绑' : '确认绑定' }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getUserInfo, updateUserInfo, bindPhone, sendSmsCode } from '../../api/user'
import { uploadMiniImage } from '../../utils/upload'

const MOBILE_RE = /^1[3-9]\d{9}$/

const nickname = ref('')
const avatarPreview = ref('')
const avatarUrl = ref('')
const originalAvatar = ref('')
const phone = ref('')
const avatarUploading = ref(false)
const showBind = ref(false)
const bindForm = ref({ phone: '', code: '' })
const sending = ref(false)
const countdown = ref(0)
let countdownTimer = null

const phoneText = computed(() => {
  const p = phone.value
  if (!p) return '未绑定'
  return p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p
})

onLoad(async () => {
  const cached = uni.getStorageSync('userInfo') || {}
  nickname.value = cached.nickname || ''
  avatarPreview.value = cached.avatar || ''
  avatarUrl.value = cached.avatar || ''
  originalAvatar.value = cached.avatar || ''
  phone.value = cached.phone || ''
  try {
    const res = await getUserInfo()
    if (res.code === 0 && res.data) {
      nickname.value = res.data.nickname || ''
      avatarPreview.value = res.data.avatar || ''
      avatarUrl.value = res.data.avatar || ''
      originalAvatar.value = res.data.avatar || ''
      phone.value = res.data.phone || ''
      uni.setStorageSync('userInfo', res.data)
    }
  } catch (e) {
    console.warn('[Profile] 资料加载失败，使用本地缓存', e)
  }
})

function onNicknameBlur(e) {
  nickname.value = e?.detail?.value || ''
}

// #ifdef MP-WEIXIN
function onWxAvatar(e) {
  const path = e?.detail?.avatarUrl
  if (path) uploadAvatar(path)
}
// #endif

function onLocalAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => uploadAvatar(res.tempFilePaths[0])
  })
}

async function uploadAvatar(filePath) {
  avatarPreview.value = filePath
  avatarUploading.value = true
  try {
    const url = await uploadMiniImage(filePath, 'user/avatar')
    avatarPreview.value = url
    avatarUrl.value = url
  } catch (e) {
    console.warn('[Profile] 头像上传失败', e)
    avatarPreview.value = avatarUrl.value
  } finally {
    avatarUploading.value = false
  }
}

async function onSave() {
  if (avatarUploading.value) {
    uni.showToast({ title: '头像上传中，请稍候', icon: 'none' })
    return
  }
  const name = nickname.value.trim()
  if (name.length < 2 || name.length > 20) {
    uni.showToast({ title: '昵称长度需为 2-20 个字符', icon: 'none' })
    return
  }

  const payload = { nickname: name }
  if (avatarUrl.value && avatarUrl.value !== originalAvatar.value) {
    payload.avatar = avatarUrl.value
  }

  const res = await updateUserInfo(payload)
  if (res.code === 0) {
    const cached = uni.getStorageSync('userInfo') || {}
    uni.setStorageSync('userInfo', { ...cached, nickname: name, avatar: avatarUrl.value })
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } else {
    uni.showToast({ title: res.message || '保存失败', icon: 'none' })
  }
}

function openBind() {
  bindForm.value = { phone: '', code: '' }
  showBind.value = true
}

function closeBind() {
  showBind.value = false
}

function startCountdown() {
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(countdownTimer)
  }, 1000)
}

onUnload(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

async function onSendCode() {
  if (sending.value || countdown.value > 0) return
  if (!MOBILE_RE.test(bindForm.value.phone)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  sending.value = true
  try {
    const res = await sendSmsCode(bindForm.value.phone)
    if (res.code === 0) {
      startCountdown()
      if (res.data?.code) {
        bindForm.value.code = String(res.data.code)
        uni.showToast({ title: '验证码已自动填入', icon: 'none' })
      } else {
        uni.showToast({ title: '验证码已发送', icon: 'none' })
      }
    } else {
      uni.showToast({ title: res.message || '发送失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
  } finally {
    sending.value = false
  }
}

async function onBindSubmit() {
  const p = bindForm.value.phone
  const code = String(bindForm.value.code || '')
  if (!MOBILE_RE.test(p)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  if (!/^\d{6}$/.test(code)) {
    uni.showToast({ title: '请输入 6 位验证码', icon: 'none' })
    return
  }
  try {
    const res = await bindPhone(p, code)
    if (res.code === 0) {
      phone.value = p
      const cached = uni.getStorageSync('userInfo') || {}
      uni.setStorageSync('userInfo', { ...cached, phone: p })
      closeBind()
      uni.showToast({ title: '已绑定', icon: 'success' })
    } else {
      uni.showToast({ title: res.message || '绑定失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.pf-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
  padding-bottom: 40rpx;
}

.card {
  background: $uni-bg-color;
  margin: $uni-spacing-col-lg;
  border-radius: $uni-border-radius-base;
  box-shadow: $uni-shadow-sm;
  overflow: hidden;
}

.row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: $uni-spacing-col-lg;
  min-height: 104rpx;
  box-sizing: border-box;
  border-bottom: 2rpx solid $uni-border-color-divider;
}

.row--last {
  border-bottom: none;
}

.row--tap {
  transition: opacity 150ms ease-out;
}

.row--tap:active {
  opacity: 0.75;
}

.row-label {
  flex: none;
  width: 120rpx;
  font-size: 28rpx;
  color: $uni-text-color;
}

.row-avatar {
  margin-left: auto;
  padding: 0;
  border: none;
  background: transparent;
  line-height: 1;
  display: flex;
  align-items: center;

  &::after {
    border: none;
  }
}

.avatar {
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: $uni-bg-color-img;
  border: 2rpx solid $uni-border-color;
}

.avatar--ph {
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.avatar-ic) {
  color: $uni-color-img-icon;
}

.row-state {
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
}

.row-input {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: 28rpx;
  color: $uni-text-color;
}

.row-value {
  margin-left: auto;
  font-size: 28rpx;
  color: $uni-text-color-placeholder;
}

:deep(.row-chev) {
  color: $uni-text-color-disable;
}

/* ===== 手机号绑定抽屉 ===== */
.mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: $uni-bg-color-mask;
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
  background: $uni-bg-color;
  border-radius: $uni-border-radius-xl $uni-border-radius-xl 0 0;
  padding: $uni-spacing-col-lg;
  padding-bottom: calc(#{$uni-spacing-col-lg} + env(safe-area-inset-bottom));
}

.sheet-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sheet-title {
  font-size: 34rpx;
  font-weight: 600;
  color: $uni-text-color;
}

.sheet-close {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.sheet-close-ic) {
  color: $uni-text-color-placeholder;
}

.sheet-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $uni-text-color-placeholder;
}

.field {
  margin-top: $uni-spacing-col-lg;
}

.field-label {
  display: block;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  color: $uni-text-color-grey;
}

.field-input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border: 2rpx solid $uni-border-color-strong;
  border-radius: $uni-border-radius-sm;
  font-size: 28rpx;
  color: $uni-text-color;
  background: $uni-bg-color;
}

.code-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.code-input {
  flex: 1;
  min-width: 0;
}

.code-btn {
  flex: none;
  width: 200rpx;
  height: 88rpx;
  line-height: 84rpx;
  text-align: center;
  font-size: 26rpx;
  font-weight: 500;
  color: $uni-color-primary;
  background: $uni-color-primary-50;
  border: 2rpx solid $uni-color-primary-100;
  border-radius: $uni-border-radius-sm;
  box-sizing: border-box;
}

.code-btn.is-disabled {
  color: $uni-text-color-placeholder;
  background: $uni-bg-color-muted;
  border-color: $uni-border-color;
}

.sheet-save {
  margin-top: 40rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: $uni-color-primary;
  color: $uni-text-color-inverse;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;

  &:active {
    background: $uni-color-primary-dark;
  }
}

.save-wrap {
  padding: 40rpx $uni-spacing-col-lg 0;
}

.save-btn {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: $uni-color-primary;
  color: $uni-text-color-inverse;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;

  &:active {
    background: $uni-color-primary-dark;
  }

  &.is-disabled {
    opacity: $uni-opacity-disabled;
  }
}
</style>
