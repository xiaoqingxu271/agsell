<script setup lang="ts">
import { computed } from 'vue'
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
  RefreshLeft,
  SwitchButton,
} from '@element-plus/icons-vue'

const router = useRouter()
const adminStore = useAdminStore()

const displayName = computed(() => adminStore.adminInfo?.realName ?? '管理员')

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
    <!-- 侧边栏 -->
    <el-aside width="200px" class="aside">
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
        <span class="logo-text">农产品销售管理</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        class="sidebar-menu"
        background-color="#14532D"
        text-color="rgba(255,255,255,0.78)"
        active-text-color="#FFFFFF"
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
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <span class="breadcrumb">首页 / <span class="breadcrumb-current">{{ $route.meta.title || '管理后台' }}</span></span>
        </div>
        <div class="header-right">
          <span class="admin-name">{{ displayName }}</span>
          <el-button type="danger" text size="small" @click="handleLogout">
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
}

.aside {
  background: #14532D;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}

.logo-icon {
  color: #FFFFFF;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
}

.logo-text {
  color: #FFFFFF;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.sidebar-menu {
  flex: 1;
  border: none;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 200px;
}

/* 激活项：底 #15803D + 纯白字 + 左侧 3px #22C55E 指示条 */
.sidebar-menu .el-menu-item.is-active {
  background-color: #15803D !important;
  color: #FFFFFF !important;
  position: relative;
}
.sidebar-menu .el-menu-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background-color: #22C55E;
}

.sidebar-menu .el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.08) !important;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #E5E7EB;
  background: #FFFFFF;
  padding: 0 20px;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.breadcrumb {
  font-size: 14px;
  color: #6B7280;
}

.breadcrumb-current {
  color: #15803D;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-name {
  font-size: 14px;
  color: #1F2937;
}

.main {
  background: #F0FDF4;
  padding: 20px;
}

.mr-1 {
  margin-right: 4px;
}

/* <768px 侧栏折叠为 64px 图标模式 */
@media (max-width: 767px) {
  .aside {
    width: 64px !important;
    min-width: 64px !important;
  }
  .logo-text {
    display: none;
  }
  .sidebar-menu {
    width: 64px !important;
  }
  .sidebar-menu .el-menu-item span {
    display: none;
  }
}
</style>
