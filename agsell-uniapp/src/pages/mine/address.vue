<template>
  <view class="address-page">
    <NavBar title="收货地址" />

    <view class="content">
      <!-- 未登录时显示登录引导 -->
      <view v-if="!isLoggedInUser" class="empty-state" @click="onLogin">
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
          <circle cx="12" cy="10" r="3"></circle>
        </svg>
        <text class="empty-text">登录后管理您的收货地址</text>
        <view class="login-hint-btn">微信一键登录</view>
      </view>

      <!-- 已登录但无地址 -->
      <view v-else-if="addresses.length === 0" class="empty-state">
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
        </svg>
        <text class="empty-text">暂无收货地址</text>
      </view>

      <!-- 地址列表 -->
      <view
        v-for="(addr, index) in addresses"
        :key="addr.id"
        class="address-card card"
        :class="{ 'is-default': addr.isDefault === 1 }"
      >
        <view class="addr-main" @click="onEdit(addr)">
          <view class="addr-info">
            <view class="addr-person">
              <text class="receiver">{{ addr.receiver }}</text>
              <text class="phone">{{ addr.phone }}</text>
              <text v-if="addr.tag" class="tag tag-accent">{{ addr.tag }}</text>
              <text v-if="addr.isDefault === 1" class="tag tag-success">默认</text>
            </view>
            <text class="addr-detail">{{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}</text>
          </view>
          <view class="addr-actions">
            <text class="action-btn action-edit" @click.stop="onEdit(addr)">编辑</text>
            <text class="action-btn action-delete" @click.stop="onDelete(addr)">删除</text>
            <text
              v-if="addr.isDefault !== 1"
              class="action-btn action-default"
              @click.stop="onSetDefault(addr)"
            >设为默认</text>
          </view>
        </view>
      </view>

      <!-- 新增地址按钮 -->
      <view v-if="isLoggedInUser" class="add-btn" @click="onEdit(null)" role="button">
        <svg class="add-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <line x1="12" y1="5" x2="12" y2="19"></line>
          <line x1="5" y1="12" x2="19" y2="12"></line>
        </svg>
        <text class="add-text">新增收货地址</text>
      </view>
    </view>

    <!-- 新增地址弹窗 -->
    <view v-if="showEditForm" class="edit-mask" @click="closeEdit">
      <view class="edit-content" @click.stop>
        <view class="edit-header">
          <text class="edit-title">{{ editingId ? '编辑地址' : '新增地址' }}</text>
          <view class="edit-close" @click="closeEdit" role="button" aria-label="关闭">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </view>
        </view>
        <view class="form-item">
          <text class="form-label">收件人</text>
          <input class="form-input" v-model="form.receiver" placeholder="请输入收件人姓名" placeholder-style="color:#9CA3AF" />
        </view>
        <view class="form-item">
          <text class="form-label">手机号</text>
          <input class="form-input" type="number" v-model="form.phone" placeholder="请输入手机号" placeholder-style="color:#9CA3AF" />
        </view>
        <view class="form-item">
          <text class="form-label">所在地区</text>
          <view class="region-row">
            <picker mode="region" @change="onRegionChange" :value="form.region">
              <view class="region-picker">
                {{ form.province || '请选择省' }} / {{ form.city || '市' }} / {{ form.district || '区' }}
              </view>
            </picker>
          </view>
        </view>
        <view class="form-item">
          <text class="form-label">详细地址</text>
          <input class="form-input" v-model="form.detail" placeholder="街道/门牌号等" placeholder-style="color:#9CA3AF" />
        </view>
        <view class="form-item">
          <text class="form-label">标签</text>
          <view class="tag-options">
            <view
              v-for="tag in ['家', '公司', '学校']"
              :key="tag"
              class="tag-option"
              :class="{ active: form.tag === tag }"
              @click="form.tag = form.tag === tag ? '' : tag"
              role="button"
            >
              {{ tag }}
            </view>
          </view>
        </view>
        <view class="form-item default-row">
          <view class="default-toggle" @click="form.isDefault = form.isDefault ? 0 : 1" role="switch" :aria-checked="form.isDefault === 1">
            <text class="toggle-text">设为默认地址</text>
            <view class="toggle-switch" :class="{ on: form.isDefault === 1 }">
              <view class="toggle-circle" />
            </view>
          </view>
        </view>
        <view class="save-btn" @click="onSave" role="button">保存</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onMounted } from 'vue'
import NavBar from '../../components/NavBar/NavBar.vue'
import { getAddressList, addAddress, updateAddress, deleteAddress, setDefaultAddress } from '../../api/user'
import { wxLogin, isLoggedIn } from '../../utils/request'

const addresses = ref([])
const showEditForm = ref(false)
const editingId = ref(null)
const isLoggedInUser = ref(isLoggedIn())
const form = ref({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  tag: '',
  isDefault: 0,
  region: []
})

onMounted(async () => {
  isLoggedInUser.value = isLoggedIn()
  if (isLoggedInUser.value) {
    await loadAddresses()
  }
})

async function loadAddresses() {
  const res = await getAddressList()
  if (res.code === 0) {
    addresses.value = res.data || []
  }
}

async function onLogin() {
  uni.showLoading({ title: '登录中...', mask: true })
  const success = await wxLogin()
  uni.hideLoading()
  if (success) {
    isLoggedInUser.value = true
    uni.showToast({ title: '登录成功', icon: 'success' })
    await loadAddresses()
  }
}

function onEdit(addr) {
  if (!isLoggedInUser.value) {
    onLogin()
    return
  }
  editingId.value = addr?.id || null
  if (addr) {
    form.value = {
      receiver: addr.receiver,
      phone: addr.phone,
      province: addr.province || '',
      city: addr.city || '',
      district: addr.district || '',
      detail: addr.detail,
      tag: addr.tag || '',
      isDefault: addr.isDefault || 0,
      region: [addr.province, addr.city, addr.district].filter(Boolean)
    }
  } else {
    form.value = { receiver: '', phone: '', province: '', city: '', district: '', detail: '', tag: '', isDefault: 0, region: [] }
  }
  showEditForm.value = true
}

function closeEdit() {
  showEditForm.value = false
  editingId.value = null
}

function onRegionChange(e) {
  const val = e.detail.value
  form.value.region = val
  form.value.province = val[0] || ''
  form.value.city = val[1] || ''
  form.value.district = val[2] || ''
}

async function onSave() {
  if (!isLoggedInUser.value) {
    await onLogin()
    if (!isLoggedInUser.value) return
  }
  const { receiver, phone, province, city, district, detail } = form.value
  if (!receiver.trim() || !phone.trim() || !detail.trim()) {
    uni.showToast({ title: '请填写必要信息', icon: 'none' })
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    uni.showToast({ title: '手机号格式不正确', icon: 'none' })
    return
  }

  const data = {
    receiver, phone,
    province, city, district, detail,
    tag: form.value.tag,
    isDefault: form.value.isDefault
  }

  let res
  if (editingId.value) {
    res = await updateAddress(editingId.value, data)
  } else {
    res = await addAddress(data)
  }

  if (res.code === 0) {
    uni.showToast({ title: '保存成功', icon: 'success' })
    closeEdit()
    loadAddresses()
  } else {
    uni.showToast({ title: res.message || '保存失败', icon: 'none' })
  }
}

async function onDelete(addr) {
  if (!isLoggedInUser.value) {
    await onLogin()
    if (!isLoggedInUser.value) return
  }
  uni.showModal({
    title: '提示',
    content: '确定删除该地址？',
    success: async (res) => {
      if (res.confirm) {
        await deleteAddress(addr.id)
        uni.showToast({ title: '删除成功', icon: 'success' })
        loadAddresses()
      }
    }
  })
}

async function onSetDefault(addr) {
  if (!isLoggedInUser.value) {
    await onLogin()
    if (!isLoggedInUser.value) return
  }
  await setDefaultAddress(addr.id)
  uni.showToast({ title: '已设为默认地址', icon: 'success' })
  loadAddresses()
}
</script>

<style scoped>
.address-page {
  min-height: 100vh;
  background: #F0FDF4;
}

.content {
  padding: 24rpx;
  padding-bottom: 160rpx;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 160rpx 40rpx;
  text-align: center;
}

.empty-icon {
  width: 96rpx;
  height: 96rpx;
  color: #9CA3AF;
  margin-bottom: 24rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #9CA3AF;
  margin-bottom: 32rpx;
}

.login-hint-btn {
  background: #15803D;
  color: #FFFFFF;
  padding: 20rpx 48rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
  font-weight: 600;
  min-height: 88rpx;
  line-height: 88rpx;
  box-sizing: border-box;
  padding-top: 0;
  padding-bottom: 0;
}

/* 新增地址按钮 */
.add-btn {
  position: fixed;
  bottom: 40rpx;
  left: 50%;
  transform: translateX(-50%);
  background: #15803D;
  color: #FFFFFF;
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 0 56rpx;
  height: 88rpx;
  border-radius: 44rpx;
  box-shadow: 0 4rpx 16rpx rgba(21, 128, 61, 0.3);
  min-width: 320rpx;
  justify-content: center;
  padding-bottom: calc(0px + env(safe-area-inset-bottom));
}

.add-icon {
  width: 36rpx;
  height: 36rpx;
  color: #FFFFFF;
}

.add-text {
  font-size: 30rpx;
  font-weight: 600;
}

.card {
  background: #FFFFFF;
  border-radius: 24rpx;
  margin-bottom: 16rpx;
  overflow: hidden;
  border: 1px solid #BBF7D0;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
}

.address-card.is-default {
  border-color: #15803D;
}

.addr-main {
  padding: 24rpx;
}

.addr-person {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 12rpx;
  flex-wrap: wrap;
}

.receiver {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}

.phone {
  font-size: 26rpx;
  color: #6B7280;
}

.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  line-height: 1.4;
}

.tag-accent {
  background: #FFF7ED;
  color: #9A3412;
}

.tag-success {
  background: #DCFCE7;
  color: #166534;
}

.addr-detail {
  font-size: 26rpx;
  color: #6B7280;
  line-height: 1.6;
}

.addr-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1px solid #E5E7EB;
  flex-wrap: wrap;
}

.action-btn {
  font-size: 24rpx;
  padding: 8rpx 20rpx;
  border-radius: 8rpx;
  min-height: 60rpx;
  line-height: 60rpx;
  box-sizing: border-box;
  padding-top: 0;
  padding-bottom: 0;
}

.action-edit {
  color: #15803D;
  border: 1px solid #15803D;
}

.action-delete {
  color: #DC2626;
  border: 1px solid #DC2626;
}

.action-default {
  color: #A16207;
  border: 1px solid #A16207;
}

/* 编辑弹窗 */
.edit-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.edit-content {
  width: 100%;
  background: #FFFFFF;
  border-radius: 32rpx 32rpx 0 0;
  padding: 32rpx;
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
}

.edit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32rpx;
}

.edit-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}

.edit-close {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9CA3AF;
}

.edit-close svg {
  width: 36rpx;
  height: 36rpx;
}

.form-item {
  margin-bottom: 24rpx;
}

.form-label {
  font-size: 28rpx;
  color: #6B7280;
  margin-bottom: 12rpx;
  display: block;
  font-weight: 500;
}

.form-input {
  width: 100%;
  height: 88rpx;
  border: 1px solid #D1D5DB;
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #1F2937;
  box-sizing: border-box;
  background: #FFFFFF;
}

.region-row {
  padding: 0 24rpx;
  background: #FFFFFF;
  border-radius: 12rpx;
  border: 1px solid #D1D5DB;
  height: 88rpx;
  display: flex;
  align-items: center;
}

.region-picker {
  font-size: 28rpx;
  color: #1F2937;
}

.tag-options {
  display: flex;
  gap: 16rpx;
}

.tag-option {
  padding: 12rpx 32rpx;
  border: 1px solid #BBF7D0;
  border-radius: 44rpx;
  font-size: 26rpx;
  color: #6B7280;
  min-height: 60rpx;
  line-height: 60rpx;
  box-sizing: border-box;
  padding-top: 0;
  padding-bottom: 0;
}

.tag-option.active {
  background: #15803D;
  color: #FFFFFF;
  border-color: #15803D;
}

.default-row {
  padding: 20rpx 0;
}

.default-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
}

.toggle-text {
  font-size: 28rpx;
  color: #1F2937;
}

.toggle-switch {
  width: 88rpx;
  height: 48rpx;
  background: #D1D5DB;
  border-radius: 16rpx;
  position: relative;
  transition: background 0.2s ease;
  flex-shrink: 0;
}

.toggle-switch.on {
  background: #15803D;
}

.toggle-circle {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 40rpx;
  height: 40rpx;
  background: #FFFFFF;
  border-radius: 50%;
  transition: left 0.2s ease;
}

.toggle-switch.on .toggle-circle {
  left: 44rpx;
}

.save-btn {
  background: #15803D;
  color: #FFFFFF;
  text-align: center;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 32rpx;
  font-weight: 600;
  margin-top: 40rpx;
}
</style>
