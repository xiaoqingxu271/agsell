<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminLogin } from '@/api/admin'
import { useAdminStore } from '@/stores/admin'
import type { AdminLoginRequest } from '@/types'

const router = useRouter()
const adminStore = useAdminStore()

const form = ref<AdminLoginRequest>({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    const res = await adminLogin(form.value)
    adminStore.setToken(res.data.token)
    adminStore.setAdminInfo(res.data)

    const redirect = router.currentRoute.value.query.redirect as string | undefined
    router.push(redirect || '/admin/dashboard')
  } catch {
    // request interceptor already shows ElMessage
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="card">
      <div class="card-header">
        <div class="logo">🌾</div>
        <h1 class="title">管理后台</h1>
        <p class="subtitle">请登录以继续操作</p>
      </div>

      <el-form
        :model="form"
        label-position="top"
        size="large"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="用户名">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            clearable
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="footer">
        <span>农产品销售系统</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.card {
  width: 380px;
  background: #fff;
  border-radius: 8px;
  padding: 2rem;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.card-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.logo {
  font-size: 2rem;
  margin-bottom: 0.5rem;
}

.title {
  margin: 0 0 0.25rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: #303133;
}

.subtitle {
  margin: 0;
  font-size: 0.85rem;
  color: #909399;
}

.footer {
  text-align: center;
  margin-top: 1.5rem;
  font-size: 0.75rem;
  color: #c0c4cc;
}
</style>
