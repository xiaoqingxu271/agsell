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
    <!-- 左侧品牌区（<1024px 隐藏） -->
    <div class="brand-panel" aria-hidden="true">
      <div class="brand-inner">
        <div class="brand-logo">
          <div class="brand-logo-badge">
            <svg
              class="logo-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#FFFFFF"
              stroke-width="1.7"
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
          </div>
          <span class="brand-name">农臻 Agsell<small>农产品销售运营管理平台</small></span>
        </div>
        <h1 class="brand-slogan">让每一份好物<br />都能被 <em>高效运营</em></h1>
        <p class="brand-desc">商品 · 订单 · 用户 · 评价 · 溯源 · 秒杀，一体化运营管理中台<br />数据驱动决策，让农产品生意更简单</p>
        <div class="brand-tags">
          <span class="brand-tag"><i></i>数据概览</span>
          <span class="brand-tag"><i></i>智能运营</span>
          <span class="brand-tag"><i></i>全链路溯源</span>
        </div>

        <!-- 经营概览插画卡 -->
        <div class="brand-card">
          <div class="bc-basket">
            <svg width="120" height="120" viewBox="0 0 132 132" fill="none">
              <path d="M30 76h72l-8 36a8 8 0 0 1-8 6H46a8 8 0 0 1-8-6l-8-36z" fill="#E8A33D" opacity="0.16" />
              <path d="M30 76h72l-4 16H34l-4-16z" fill="#D97706" opacity="0.22" />
              <path d="M30 76h72" stroke="#C97B14" stroke-width="3" stroke-linecap="round" />
              <circle cx="54" cy="64" r="17" fill="#22A55A" />
              <circle cx="54" cy="64" r="17" fill="none" stroke="#166534" stroke-width="1" opacity="0.3" />
              <path d="M54 49c-2-6 1-9 5-11" stroke="#166534" stroke-width="2.4" stroke-linecap="round" fill="none" />
              <circle cx="82" cy="70" r="15" fill="#F59E0B" />
              <circle cx="82" cy="70" r="15" fill="none" stroke="#B45309" stroke-width="1" opacity="0.3" />
              <path d="M82 56.5c-1.6-5 1.2-7.8 4.4-9.6" stroke="#B45309" stroke-width="2.2" stroke-linecap="round" fill="none" />
              <circle cx="36" cy="72" r="11" fill="#EF4444" opacity="0.9" />
              <path d="M36 62.5c-1.2-3.8 1-5.8 3.4-7.2" stroke="#B91C1C" stroke-width="2" stroke-linecap="round" fill="none" />
              <path d="M96 30c0 10-2 16-6 20-3 3-7 4-10 5" stroke="#D97706" stroke-width="2.6" stroke-linecap="round" />
              <g stroke="#E8A33D" stroke-width="2.4" stroke-linecap="round">
                <path d="M90 33c-4-2-6-5-6-9" /><path d="M93 37c-4-2-7-4-8-8" />
                <path d="M96 41c-4-2-7-3-9-7" /><path d="M98 46c-3-2-5-4-6-7" />
              </g>
            </svg>
          </div>
          <div class="bc-info">
            <div class="bc-title">丰收季 · 今日经营概览</div>
            <div class="bc-sub">数据更新于 10:32 · 实时同步</div>
            <div class="bc-metrics">
              <div class="bc-metric"><b>¥ 128,430</b><span>今日销售额</span></div>
              <div class="bc-metric gold"><b>1,028</b><span>今日活跃用户</span></div>
              <div class="bc-metric"><b>326</b><span>今日订单</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-panel">
      <div class="card">
        <div class="card-header">
          <div class="kicker">ADMIN CONSOLE</div>
          <h2 class="title">欢迎回来</h2>
          <p class="subtitle">登录农产品运营管理平台，继续高效工作</p>
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
          <span>首次使用？<em>联系管理员开通账号</em></span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  background: #F6F8F7;
}

/* ── 左侧品牌区：晨雾青绿渐变 ── */
.brand-panel {
  flex: 1.15;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(120% 90% at 85% 8%, rgba(255, 255, 255, 0.55) 0%, rgba(255, 255, 255, 0) 46%),
    radial-gradient(90% 70% at 8% 100%, rgba(52, 199, 123, 0.14) 0%, rgba(52, 199, 123, 0) 55%),
    linear-gradient(150deg, #EFF8F1 0%, #DFF1E6 55%, #CFEBD9 100%);
  overflow: hidden;
}

/* 装饰圆环 */
.brand-panel::after {
  content: '';
  position: absolute;
  right: -140px;
  bottom: -140px;
  width: 440px;
  height: 440px;
  border-radius: 50%;
  border: 1px solid rgba(22, 101, 52, 0.10);
  box-shadow: 0 0 0 52px rgba(22, 101, 52, 0.04), 0 0 0 104px rgba(22, 101, 52, 0.025);
}

.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 520px;
  padding: 40px 48px;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 44px;
}

.brand-logo-badge {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  background: linear-gradient(135deg, #15803D, #34C77B);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(21, 128, 61, 0.28);
  flex-shrink: 0;
}

.logo-icon {
  width: 24px;
  height: 24px;
}

.brand-name {
  color: #10231A;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.brand-name small {
  display: block;
  font-size: 11px;
  font-weight: 500;
  color: #5B6B63;
  letter-spacing: 0.08em;
  margin-top: 2px;
}

.brand-slogan {
  margin: 0 0 16px;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.01em;
  color: #10231A;
}

.brand-slogan em {
  font-style: normal;
  color: #15803D;
}

.brand-desc {
  margin: 0 0 28px;
  font-size: 14px;
  line-height: 1.8;
  color: #5B6B63;
}

.brand-tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 44px;
}

.brand-tag {
  padding: 7px 16px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 500;
  color: #166534;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(21, 128, 61, 0.16);
}

.brand-tag i {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34C77B;
  margin-right: 7px;
  vertical-align: 1px;
}

/* 经营概览插画卡 */
.brand-card {
  display: flex;
  align-items: center;
  gap: 20px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(21, 128, 61, 0.10);
  border-radius: 20px;
  box-shadow: 0 16px 40px rgba(21, 128, 61, 0.10);
  padding: 16px 20px;
  max-width: 480px;
}

.bc-basket {
  width: 132px;
  height: 128px;
  border-radius: 16px;
  background: linear-gradient(150deg, #FEF6E7, #FBEBCB);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.bc-info {
  flex: 1;
  min-width: 0;
}

.bc-title {
  font-size: 15px;
  font-weight: 700;
  color: #10231A;
  margin-bottom: 4px;
}

.bc-sub {
  font-size: 12px;
  color: #5B6B63;
  margin-bottom: 12px;
}

.bc-metrics {
  display: flex;
  gap: 22px;
}

.bc-metric b {
  display: block;
  font-size: 17px;
  font-weight: 700;
  color: #166534;
  font-variant-numeric: tabular-nums;
}

.bc-metric span {
  font-size: 11px;
  color: #8A9A91;
}

.bc-metric.gold b {
  color: #D97706;
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
  width: 416px;
  max-width: 100%;
  background: #FFFFFF;
  border-radius: 16px;
  padding: 40px 36px 32px;
  box-shadow: 0 12px 32px rgba(16, 24, 40, 0.12);
  border: 1px solid #E7ECE9;
}

.card-header {
  margin-bottom: 28px;
}

.kicker {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.14em;
  color: #22A55A;
  margin-bottom: 10px;
}

.title {
  margin: 0 0 8px;
  font-size: 27px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #10231A;
}

.subtitle {
  margin: 0;
  font-size: 13.5px;
  color: #5B6B63;
}

.card :deep(.el-form-item__label) {
  font-weight: 600;
  color: #10231A;
  padding-bottom: 6px;
}

.card :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 4px 12px;
  box-shadow: 0 0 0 1px #DDE4DF inset;
}

.card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #15803D inset, 0 0 0 4px rgba(21, 128, 61, 0.13) !important;
}

.login-btn {
  width: 100%;
  height: 46px;
  border-radius: 10px;
  background: linear-gradient(135deg, #15803D, #22A55A);
  border: none;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.16em;
  margin-top: 8px;
  box-shadow: 0 8px 20px rgba(21, 128, 61, 0.28);
  transition: box-shadow 0.2s ease-out, transform 0.15s ease-out;
}

.login-btn:hover {
  background: linear-gradient(135deg, #166534, #14532D) !important;
  box-shadow: 0 12px 26px rgba(21, 128, 61, 0.34);
  transform: translateY(-1.5px);
}

.login-btn:active {
  transform: translateY(0);
  box-shadow: 0 6px 14px rgba(21, 128, 61, 0.22);
}

.login-btn:focus-visible {
  outline: 2px solid #6EE7A8;
  outline-offset: 2px;
}

.footer {
  text-align: center;
  margin-top: 26px;
  font-size: 12.5px;
  color: #8A9A91;
}

.footer em {
  font-style: normal;
  color: #15803D;
  font-weight: 600;
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
