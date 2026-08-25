<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { adminLogout } from '@/api/admin'
import { ElMessageBox } from 'element-plus'

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
        <span class="logo-icon">🌾</span>
        <span class="logo-text">农产品销售</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        class="sidebar-menu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/admin/dashboard">
          <span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <span class="page-title">{{ $route.meta.title || '管理后台' }}</span>
        </div>
        <div class="header-right">
          <span class="admin-name">{{ displayName }}</span>
          <el-button type="danger" text size="small" @click="handleLogout">
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
  background: #304156;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid #3a4a5e;
}

.logo-icon {
  font-size: 1.5rem;
}

.logo-text {
  color: #fff;
  font-size: 0.95rem;
  font-weight: 600;
}

.sidebar-menu {
  flex: 1;
  border: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
  padding: 0 1.25rem;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.page-title {
  font-size: 1rem;
  font-weight: 600;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.admin-name {
  font-size: 0.875rem;
  color: #606266;
}

.main {
  background: #f0f2f5;
  padding: 1.25rem;
}
</style>
