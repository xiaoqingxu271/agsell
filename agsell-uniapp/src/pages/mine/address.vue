<template>
  <view class="address-page">
    <NavBar title="收货地址" />

    <view class="content">
      <!-- 未登录时显示登录引导 -->
      <view v-if="!isLoggedInUser" class="empty-state" @click="onLogin">
        <text class="empty-icon">📍</text>
        <text class="empty-text">登录后管理您的收货地址</text>
        <view class="login-hint-btn">微信一键登录</view>
      </view>

      <!-- 已登录但无地址 -->
      <view v-else-if="addresses.length === 0" class="empty-state">
        <text class="empty-icon">📭</text>
        <text class="empty-text">暂无收货地址</text>
      </view>

      <!-- 地址列表 -->
      <view
        v-for="(addr, index) in addresses"
        :key="addr.id"
        class="address-card card"
        :class="{ default: addr.isDefault === 1 }"
      >
        <view class="addr-main" @click="onEdit(addr)">
          <view class="addr-info">
            <view class="addr-person">
              <text class="receiver">{{ addr.receiver }}</text>
              <text class="phone">{{ addr.phone }}</text>
              <text v-if="addr.tag" class="tag">{{ addr.tag }}</text>
              <text v-if="addr.isDefault === 1" class="default-tag">默认</text>
            </view>
            <text class="addr-detail">{{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}</text>
          </view>
          <view class="addr-actions">
            <text class="edit-btn" @click.stop="onEdit(addr)">编辑</text>
            <text class="del-btn" @click.stop="onDelete(addr)">删除</text>
            <text
              v-if="addr.isDefault !== 1"
              class="default-btn"
              @click.stop="onSetDefault(addr)"
            >设为默认</text>
          </view>
        </view>
      </view>

      <!-- 新增地址按钮 -->
      <view v-if="isLoggedInUser" class="add-btn" @click="onEdit(null)">
        <text class="add-icon">+</text>
        <text class="add-text">新增收货地址</text>
      </view>
    </view>

    <!-- 新增地址弹窗 -->
    <view v-if="showEditForm" class="edit-mask" @click="closeEdit">
      <view class="edit-content" @click.stop>
        <view class="edit-header">
          <text class="edit-title">{{ editingId ? '编辑地址' : '新增地址' }}</text>
          <text class="edit-close" @click="closeEdit">×</text>
        </view>
        <view class="form-item">
          <text class="form-label">收件人</text>
          <input class="form-input" v-model="form.receiver" placeholder="请输入收件人姓名" />
        </view>
        <view class="form-item">
          <text class="form-label">手机号</text>
          <input class="form-input" type="number" v-model="form.phone" placeholder="请输入手机号" />
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
          <input class="form-input" v-model="form.detail" placeholder="街道/门牌号等" />
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
            >
              {{ tag }}
            </view>
          </view>
        </view>
        <view class="form-item default-row">
          <view class="default-toggle" @click="form.isDefault = form.isDefault ? 0 : 1">
            <text class="toggle-text">设为默认地址</text>
            <view class="toggle-switch" :class="{ on: form.isDefault === 1 }">
              <view class="toggle-circle" />
            </view>
          </view>
        </view>
        <view class="save-btn" @click="onSave">保存</view>
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
  background: #f5f5f5;
}

.content {
  padding: 20rpx;
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
  font-size: 96rpx;
  margin-bottom: 24rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #999;
  margin-bottom: 32rpx;
}

.login-hint-btn {
  background: #4CAF50;
  color: #fff;
  padding: 20rpx 48rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
}

/* 新增地址按钮 */
.add-btn {
  position: fixed;
  bottom: 40rpx;
  left: 50%;
  transform: translateX(-50%);
  background: #4CAF50;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 56rpx;
  border-radius: 48rpx;
  box-shadow: 0 4rpx 16rpx rgba(76, 175, 80, 0.4);
  min-width: 320rpx;
  justify-content: center;
}

.add-icon {
  font-size: 36rpx;
  font-weight: bold;
}

.add-text {
  font-size: 30rpx;
  font-weight: bold;
}

.card {
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
  overflow: hidden;
}

.address-card.default {
  border: 2rpx solid #4CAF50;
}

.addr-main {
  padding: 24rpx;
}

.addr-person {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.receiver {
  font-size: 32rpx;
  font-weight: bold;
  color: #333;
}

.phone {
  font-size: 26rpx;
  color: #666;
}

.tag {
  font-size: 20rpx;
  color: #fff;
  background: #4CAF50;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.default-tag {
  font-size: 20rpx;
  color: #fff;
  background: #FF9800;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.addr-detail {
  font-size: 26rpx;
  color: #666;
  line-height: 1.6;
}

.addr-actions {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f5f5;
}

.edit-btn, .del-btn, .default-btn {
  font-size: 24rpx;
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
}

.edit-btn { color: #4CAF50; border: 1rpx solid #4CAF50; }
.del-btn { color: #F44336; border: 1rpx solid #F44336; }
.default-btn { color: #FF9800; border: 1rpx solid #FF9800; }

/* 编辑弹窗 */
.edit-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.edit-content {
  width: 100%;
  background: #fff;
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
  font-weight: bold;
  color: #333;
}

.edit-close {
  font-size: 48rpx;
  color: #999;
  line-height: 1;
}

.form-item {
  margin-bottom: 24rpx;
}

.form-label {
  font-size: 28rpx;
  color: #333;
  margin-bottom: 12rpx;
  display: block;
}

.form-input {
  width: 100%;
  height: 80rpx;
  border: 1rpx solid #eee;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

.region-row {
  padding: 20rpx;
  background: #f5f5f5;
  border-radius: 12rpx;
  border: 1rpx solid #eee;
}

.region-picker {
  font-size: 28rpx;
  color: #333;
}

.tag-options {
  display: flex;
  gap: 16rpx;
}

.tag-option {
  padding: 12rpx 32rpx;
  border: 1rpx solid #eee;
  border-radius: 28rpx;
  font-size: 26rpx;
  color: #666;
}

.tag-option.active {
  background: #4CAF50;
  color: #fff;
  border-color: #4CAF50;
}

.default-row {
  padding: 20rpx 0;
}

.default-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toggle-text {
  font-size: 28rpx;
  color: #333;
}

.toggle-switch {
  width: 80rpx;
  height: 44rpx;
  background: #ccc;
  border-radius: 22rpx;
  position: relative;
  transition: background 0.3s;
}

.toggle-switch.on {
  background: #4CAF50;
}

.toggle-circle {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 36rpx;
  height: 36rpx;
  background: #fff;
  border-radius: 50%;
  transition: left 0.3s;
}

.toggle-switch.on .toggle-circle {
  left: 40rpx;
}

.save-btn {
  background: #4CAF50;
  color: #fff;
  text-align: center;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  font-size: 32rpx;
  margin-top: 40rpx;
}
</style>
