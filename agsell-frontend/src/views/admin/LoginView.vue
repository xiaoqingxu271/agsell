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
    <!-- 左侧品牌区（<768px 隐藏） -->
    <div class="brand-panel" aria-hidden="true">
      <div class="brand-inner">
        <div class="brand-logo">
          <svg
            class="logo-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <!-- 稻穗 Logo：主茎 + 两侧谷粒 + 顶部穗 -->
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
          <span class="brand-name">农产品销售系统</span>
        </div>
        <h1 class="brand-slogan">让每一份农产品<br />都能被高效运营</h1>
        <p class="brand-desc">商品 · 订单 · 用户 · 评价 · 售后 一体化运营管理平台</p>
        <div class="brand-tags">
          <span class="brand-tag">数据概览</span>
          <span class="brand-tag">智能运营</span>
          <span class="brand-tag">安全可靠</span>
        </div>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-panel">
      <div class="card">
        <div class="card-header">
          <h2 class="title">管理后台</h2>
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
          <span>AgSell 企业运营管理平台</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  background: #F4F6F5;
}

/* ── 左侧品牌区：深翡翠渐变 ── */
.brand-panel {
  flex: 1.1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(120% 90% at 85% 10%, rgba(34, 197, 94, 0.22) 0%, transparent 46%),
    radial-gradient(90% 80% at 10% 95%, rgba(34, 197, 94, 0.12) 0%, transparent 55%),
    linear-gradient(160deg, #0E3B25 0%, #0A2A1B 100%);
  overflow: hidden;
}

/* 装饰：右下角隐约稻穗纹理（大圆环） */
.brand-panel::after {
  content: '';
  position: absolute;
  right: -140px;
  bottom: -140px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 48px rgba(255, 255, 255, 0.03), 0 0 0 96px rgba(255, 255, 255, 0.02);
}

.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 460px;
  padding: 48px;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 48px;
}

.logo-icon {
  color: #FFFFFF;
  width: 40px;
  height: 40px;
  filter: drop-shadow(0 2px 8px rgba(34, 197, 94, 0.4));
}

.brand-name {
  color: #FFFFFF;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.brand-slogan {
  margin: 0 0 16px;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.01em;
  color: #FFFFFF;
}

.brand-desc {
  margin: 0 0 32px;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.66);
}

.brand-tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.brand-tag {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.82);
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
}

/* ── 右侧表单区 ── */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.card {
  width: 400px;
  max-width: 100%;
  background: #FFFFFF;
  border-radius: 16px;
  padding: 40px 36px 32px;
  box-shadow: 0 12px 32px rgba(16, 24, 40, 0.12);
  border: 1px solid #E3E7E5;
}

.card-header {
  text-align: center;
  margin-bottom: 28px;
}

.title {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #0A2A1B;
}

.subtitle {
  margin: 0;
  font-size: 13px;
  color: #6B7280;
}

.card :deep(.el-form-item__label) {
  font-weight: 500;
  color: #1F2937;
  padding-bottom: 6px;
}

.card :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 4px 12px;
  box-shadow: 0 0 0 1px #D1D5DB inset;
}
.card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #15803D inset, 0 0 0 3px rgba(21, 128, 61, 0.15) !important;
}

.login-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  background: linear-gradient(135deg, #15803D, #166534);
  border: none;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.2em;
  margin-top: 8px;
  transition: box-shadow 0.2s ease-out, transform 0.15s ease-out;
}
.login-btn:hover {
  background: linear-gradient(135deg, #166534, #14532D) !important;
  box-shadow: 0 8px 20px rgba(21, 128, 61, 0.3);
  transform: translateY(-1px);
}
.login-btn:active {
  transform: translateY(0);
}
.login-btn:focus-visible {
  outline: 2px solid #22C55E;
  outline-offset: 2px;
}

.footer {
  text-align: center;
  margin-top: 28px;
  font-size: 12px;
  color: #9CA3AF;
}

/* ── 响应式 ── */
@media (max-width: 1023px) {
  .brand-panel {
    display: none;
  }
}

@media (max-width: 480px) {
  .form-panel {
    padding: 16px;
  }
  .card {
    padding: 32px 24px 24px;
  }
}
</style>
