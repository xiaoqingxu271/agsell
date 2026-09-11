<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { adminLogout } from '@/api/admin'
import { ElMessageBox } from 'element-plus'
import {
  DataAnalysis,
  User,
  List,
  Goods,
  ShoppingCart,
  ChatDotRound,
  Picture,
  Aim,
  RefreshLeft,
  SwitchButton,
  Fold,
  Expand,
} from '@element-plus/icons-vue'

const router = useRouter()
const adminStore = useAdminStore()

const displayName = computed(() => adminStore.adminInfo?.realName ?? '管理员')
const initial = computed(() => displayName.value.trim().charAt(0) || '管')

// ── 侧栏折叠（企业级 v3 §7：<768px 自动折叠 + 顶栏手动切换）──
const isCollapse = ref(false)
const mq = window.matchMedia('(max-width: 767px)')

function syncCollapse(e: MediaQueryList | MediaQueryListEvent) {
  isCollapse.value = e.matches
}

onMounted(() => {
  syncCollapse(mq)
  mq.addEventListener('change', syncCollapse)
})
onBeforeUnmount(() => {
  mq.removeEventListener('change', syncCollapse)
})

async function handleLogout() {
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  try {
    await adminLogout()
  } catch {
    // 接口失败也清除本地状态
  }
  adminStore.logout()
  router.push('/admin/login')
}
</script>

<template>
  <el-container class="layout">
    <!-- 侧边栏（深翡翠渐变） -->
    <el-aside :width="isCollapse ? '64px' : '240px'" class="aside">
      <div class="logo">
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
          <!-- 稻穗 Logo：主茎 + 两侧谷粒 + 顶部穗（品牌白） -->
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
        <span v-if="!isCollapse" class="logo-text">农产品销售管理</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        class="sidebar-menu"
        :collapse="isCollapse"
        :collapse-transition="false"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/categories">
          <el-icon><List /></el-icon>
          <span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/products">
          <el-icon><Goods /></el-icon>
          <span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/orders">
          <el-icon><ShoppingCart /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/reviews">
          <el-icon><ChatDotRound /></el-icon>
          <span>评价管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/after-sales">
          <el-icon><RefreshLeft /></el-icon>
          <span>售后管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/banners">
          <el-icon><Picture /></el-icon>
          <span>轮播图管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/traceability">
          <el-icon><Aim /></el-icon>
          <span>产地溯源</span>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <span v-if="!isCollapse">AgSell v3.0</span>
      </div>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <button class="collapse-btn" type="button" aria-label="切换侧栏" @click="isCollapse = !isCollapse">
            <el-icon :size="18" aria-hidden="true"><Expand v-if="isCollapse" /><Fold v-else /></el-icon>
          </button>
          <span class="breadcrumb">首页 / <span class="breadcrumb-current">{{ $route.meta.title || '管理后台' }}</span></span>
        </div>
        <div class="header-right">
          <div class="admin-chip" :title="displayName">
            <span class="admin-avatar" aria-hidden="true">{{ initial }}</span>
            <span class="admin-name">{{ displayName }}</span>
          </div>
          <el-button type="danger" text size="small" class="logout-btn" @click="handleLogout">
            <el-icon class="mr-1"><SwitchButton /></el-icon>
            退出
          </el-button>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
  background: #F4F6F5;
}

/* ── 侧栏：深翡翠渐变 ── */
.aside {
  background: linear-gradient(180deg, #0E3B25 0%, #0A2A1B 100%);
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease-out;
  overflow: hidden;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  white-space: nowrap;
}

.logo-icon {
  color: #FFFFFF;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  /* 稻穗图形在 24 视框内略偏下，上移 1.5px 与文字视觉同线 */
  transform: translateY(-1.5px);
  filter: drop-shadow(0 2px 6px rgba(34, 197, 94, 0.35));
}

.logo-text {
  color: #FFFFFF;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.sidebar-menu {
  flex: 1;
  border: none;
  background: transparent;
  padding: 8px 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}

/* 菜单项：圆角内凹块 */
.sidebar-menu .el-menu-item {
  margin: 4px 10px;
  height: 46px;
  line-height: 46px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.72);
  transition: background-color 0.15s ease-out, color 0.15s ease-out;
}
.sidebar-menu .el-menu-item .el-icon {
  font-size: 18px;
}

/* 激活项：浅白底 + 白字 + 左侧品牌指示条 */
.sidebar-menu .el-menu-item.is-active {
  background: rgba(255, 255, 255, 0.12) !important;
  color: #FFFFFF !important;
  font-weight: 500;
  position: relative;
}
.sidebar-menu .el-menu-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 2px;
  background-color: #22C55E;
}

.sidebar-menu .el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.08) !important;
  color: rgba(255, 255, 255, 0.9);
}

/* 折叠态：图标居中，隐藏文案 */
.sidebar-menu.el-menu--collapse .el-menu-item {
  margin: 4px 8px;
  padding: 0 !important;
  justify-content: center;
}

.aside-footer {
  flex-shrink: 0;
  padding: 14px 0;
  text-align: center;
  font-size: 11px;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.28);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  white-space: nowrap;
}

/* ── 顶栏 ── */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #E5E8E6;
  background: #FFFFFF;
  padding: 0 24px;
  height: 64px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #6B7280;
  cursor: pointer;
  transition: background-color 0.15s ease-out, color 0.15s ease-out;
  flex-shrink: 0;
}
.collapse-btn:hover {
  background: #F3F5F4;
  color: #15803D;
}
.collapse-btn:focus-visible {
  outline: 2px solid #22C55E;
  outline-offset: 2px;
}

.breadcrumb {
  font-size: 14px;
  color: #6B7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.breadcrumb-current {
  color: #0E3B25;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.admin-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 4px 12px 4px 4px;
  border: 1px solid #E5E8E6;
  border-radius: 999px;
  background: #FAFBFA;
}

.admin-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #15803D, #22C55E);
  color: #FFFFFF;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.admin-name {
  font-size: 13px;
  font-weight: 500;
  color: #1F2937;
  white-space: nowrap;
}

.logout-btn {
  font-weight: 500;
}

/* ── 内容区 ── */
.main {
  background: #F4F6F5;
  padding: 24px;
  overflow-y: auto;
}

.mr-1 {
  margin-right: 4px;
}

/* ── 响应式 ── */
@media (max-width: 767px) {
  .header {
    padding: 0 16px;
  }
  .main {
    padding: 16px;
  }
  .admin-name {
    display: none;
  }
  .admin-chip {
    padding: 4px;
  }
}
</style>
