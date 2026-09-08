<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminLogin } from '@/api/admin'
import { useAdminStore } from '@/stores/admin'
import type { AdminLoginRequest } from '@/types'
import { User, Lock } from '@element-plus/icons-vue'

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
    adminStore.setToken(res.token)
    adminStore.setAdminInfo(res)

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
        <svg
          class="logo-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <!-- 稻穗 Logo：主茎 + 两侧谷粒 + 顶部穗（MASTER v2.0 §8 图标规范） -->
          <path d="M12 22v-8" />
          <path d="M12 13.5c-2.1 0-3.6-1.3-4-3.6" />
          <path d="M12 16c-2.5 0-4.2-1.4-4.6-4" />
          <path d="M12 18.5c-2.9 0-4.9-1.5-5.3-4.4" />
          <path d="M12 13.5c2.1 0 3.6-1.3 4-3.6" />
          <path d="M12 16c2.5 0 4.2-1.4 4.6-4" />
          <path d="M12 18.5c2.9 0 4.9-1.5 5.3-4.4" />
          <path d="M12 12.8c-.9-1.2-1.4-2.6-1.4-4.3 0-.8.2-1.6.6-2.3" />
          <path d="M12 12.8c.9-1.2 1.4-2.6 1.4-4.3 0-.8-.2-1.6-.6-2.3" />
        </svg>
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
          >
            <template #prefix>
              <el-icon aria-hidden="true"><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <el-icon aria-hidden="true"><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-btn"
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
  background: #F0FDF4;
}

.card {
  width: 380px;
  background: #FFFFFF;
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 2px 8px rgba(16, 24, 40, 0.06);
  border: 1px solid #BBF7D0;
}

.card-header {
  text-align: center;
  margin-bottom: 24px;
}

.logo-icon {
  color: #15803D;
  width: 44px;
  height: 44px;
  margin-bottom: 8px;
}

.title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
  color: #14532D;
}

.subtitle {
  margin: 0;
  font-size: 13px;
  color: #6B7280;
}

.login-btn {
  width: 100%;
  background-color: #15803D;
  border-color: #15803D;
  font-weight: 500;
}
.login-btn:hover {
  background-color: #166534 !important;
  border-color: #166534 !important;
}
.login-btn:focus-visible {
  outline: 2px solid #22C55E;
  outline-offset: 2px;
}

.footer {
  text-align: center;
  margin-top: 24px;
  font-size: 12px;
  color: #9CA3AF;
}
</style>
