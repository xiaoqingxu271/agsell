<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { adminLogout, noticeSummary } from '@/api/admin'
import { ElMessageBox } from 'element-plus'
import {
  DataAnalysis,
  User,
  UserFilled,
  List,
  Goods,
  ShoppingCart,
  ChatDotRound,
  Picture,
  Aim,
  Timer,
  Ticket,
  RefreshLeft,
  Search,
  Setting,
  Tools,
  Document,
  SwitchButton,
  Fold,
  Expand,
  Bell,
} from '@element-plus/icons-vue'

const router = useRouter()
const adminStore = useAdminStore()

const displayName = computed(() => adminStore.adminInfo?.realName ?? '管理员')
const initial = computed(() => displayName.value.trim().charAt(0) || '管')

// ── 角色 → 可见模块（对应 系统管理模块开发文档 §3.2 权限矩阵，与后端拦截器口径一致）──
const roleModules: Record<string, string[]> = {
  SUPER_ADMIN: ['dashboard', 'users', 'categories', 'products', 'orders', 'reviews', 'afterSales', 'banners', 'traceability', 'seckill', 'coupon', 'hotWord', 'system'],
  ADMIN: ['dashboard', 'users', 'categories', 'products', 'orders', 'reviews', 'afterSales', 'banners', 'traceability', 'seckill', 'coupon', 'hotWord'],
  OPERATOR: ['dashboard', 'categories', 'products', 'orders'],
}
const currentRole = computed(() => adminStore.adminInfo?.role ?? '')
function canAccess(moduleKey: string): boolean {
  return roleModules[currentRole.value]?.includes(moduleKey) ?? false
}

// ── 侧栏折叠（v4：<768px 自动折叠 + 顶栏手动切换）──
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

// ── 通知中心（铃铛）──
const noticeVisible = ref(false)
const noticeWrapRef = ref<HTMLElement | null>(null)
const notice = ref({ pendingShipCount: 0, lowStockCount: 0, pendingAfterSalesCount: 0, total: 0 })

async function loadNotice() {
  try {
    notice.value = await noticeSummary()
  } catch { /* 静默失败，不影响主流程 */ }
}

function toggleNotice() {
  noticeVisible.value = !noticeVisible.value
}

function goNotice(path: string) {
  noticeVisible.value = false
  router.push(path)
}

function onDocClick(e: MouseEvent) {
  if (noticeVisible.value && noticeWrapRef.value && !noticeWrapRef.value.contains(e.target as Node)) {
    noticeVisible.value = false
  }
}

onMounted(() => {
  loadNotice()
  setInterval(loadNotice, 30000) // 30 秒轮询
  document.addEventListener('click', onDocClick)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
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
    <!-- 侧边栏（浅色分组导航） -->
    <el-aside :width="isCollapse ? '64px' : '236px'" class="aside">
      <div class="logo">
        <svg
          class="logo-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <!-- 稻穗 Logo：主茎 + 两侧谷粒 + 顶部穗（品牌绿） -->
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
        <span v-if="!isCollapse" class="logo-text">农臻 Agsell<small>运营管理后台</small></span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        class="sidebar-menu"
        :collapse="isCollapse"
        :collapse-transition="false"
      >
        <!-- 经营：交易结果与履约 -->
        <el-menu-item-group v-if="canAccess('dashboard') || canAccess('orders') || canAccess('afterSales')">
          <template #title><span v-show="!isCollapse" class="nav-group-title">经营</span></template>
          <el-menu-item v-if="canAccess('dashboard')" index="/admin/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据概览</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('orders')" index="/admin/orders">
            <el-icon><ShoppingCart /></el-icon>
            <span>订单管理</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('afterSales')" index="/admin/after-sales">
            <el-icon><RefreshLeft /></el-icon>
            <span>售后管理</span>
          </el-menu-item>
        </el-menu-item-group>

        <!-- 商品：卖什么 + 怎么卖 -->
        <el-menu-item-group v-if="canAccess('products') || canAccess('categories') || canAccess('seckill') || canAccess('coupon') || canAccess('traceability')">
          <template #title><span v-show="!isCollapse" class="nav-group-title">商品</span></template>
          <el-menu-item v-if="canAccess('products')" index="/admin/products">
            <el-icon><Goods /></el-icon>
            <span>商品管理</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('categories')" index="/admin/categories">
            <el-icon><List /></el-icon>
            <span>分类管理</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('seckill')" index="/admin/seckill">
            <el-icon><Timer /></el-icon>
            <span>秒杀管理</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('coupon')" index="/admin/coupon">
            <el-icon><Ticket /></el-icon>
            <span>优惠券管理</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('traceability')" index="/admin/traceability">
            <el-icon><Aim /></el-icon>
            <span>产地溯源</span>
          </el-menu-item>
        </el-menu-item-group>

        <!-- 用户：买的人 + 反馈 -->
        <el-menu-item-group v-if="canAccess('users') || canAccess('reviews')">
          <template #title><span v-show="!isCollapse" class="nav-group-title">用户</span></template>
          <el-menu-item v-if="canAccess('users')" index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('reviews')" index="/admin/reviews">
            <el-icon><ChatDotRound /></el-icon>
            <span>评价管理</span>
          </el-menu-item>
        </el-menu-item-group>

        <!-- 内容：纯前台展示配置 -->
        <el-menu-item-group v-if="canAccess('banners') || canAccess('hotWord')">
          <template #title><span v-show="!isCollapse" class="nav-group-title">内容</span></template>
          <el-menu-item v-if="canAccess('banners')" index="/admin/banners">
            <el-icon><Picture /></el-icon>
            <span>轮播图管理</span>
          </el-menu-item>
          <el-menu-item v-if="canAccess('hotWord')" index="/admin/hot-word">
            <el-icon><Search /></el-icon>
            <span>搜索热词</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group v-if="canAccess('system')">
          <template #title><span v-show="!isCollapse" class="nav-group-title">系统</span></template>
          <el-sub-menu index="system">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item index="/admin/system/admin">
              <el-icon><UserFilled /></el-icon>
              <span>管理员管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/system/config">
              <el-icon><Tools /></el-icon>
              <span>系统配置</span>
            </el-menu-item>
            <el-menu-item index="/admin/system/log">
              <el-icon><Document /></el-icon>
              <span>操作日志</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu-item-group>
      </el-menu>
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
          <div class="notice-wrap" ref="noticeWrapRef">
            <button class="icon-btn" type="button" aria-label="通知" @click="toggleNotice">
              <el-icon :size="18" aria-hidden="true"><Bell /></el-icon>
              <span v-if="notice.total > 0" class="dot" aria-hidden="true">{{ notice.total > 99 ? '99+' : notice.total }}</span>
            </button>
            <div v-if="noticeVisible" class="notice-panel">
              <div class="notice-title">待办事项</div>
              <div class="notice-item" @click="goNotice('/admin/orders')">
                <span>待发货订单</span>
                <span class="notice-num">{{ notice.pendingShipCount }} 笔</span>
              </div>
              <div class="notice-item" @click="goNotice('/admin/products')">
                <span>低库存商品</span>
                <span class="notice-num">{{ notice.lowStockCount }} 件</span>
              </div>
              <div class="notice-item" @click="goNotice('/admin/after-sales')">
                <span>待处理售后</span>
                <span class="notice-num">{{ notice.pendingAfterSalesCount }} 笔</span>
              </div>
            </div>
          </div>
          <!-- 管理员用户卡（原侧栏底部用户卡，移至顶栏右上角） -->
          <div class="header-user" :title="displayName">
            <span class="admin-avatar" aria-hidden="true">{{ initial }}</span>
            <div class="user-info">
              <div class="user-name">{{ displayName }}</div>
              <div class="user-role">{{ currentRole === 'SUPER_ADMIN' ? '超级管理员' : currentRole === 'ADMIN' ? '管理员' : '运营专员' }}</div>
            </div>
            <button class="user-logout" type="button" aria-label="退出登录" @click="handleLogout">
              <el-icon :size="17" aria-hidden="true"><SwitchButton /></el-icon>
            </button>
          </div>
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
  background: #F6F8F7;
}

/* ── 侧栏：浅色白底 ── */
.aside {
  background: #FFFFFF;
  border-right: 1px solid #E7ECE9;
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease-out;
  overflow: hidden;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid #F0F3F1;
  flex-shrink: 0;
  white-space: nowrap;
}

.logo-icon {
  color: #15803D;
  width: 25px;
  height: 25px;
  flex-shrink: 0;
  filter: drop-shadow(0 2px 6px rgba(21, 128, 61, 0.25));
}

.logo-text {
  color: #10231A;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.logo-text small {
  display: block;
  font-size: 10px;
  font-weight: 500;
  color: #8A9A91;
  letter-spacing: 0.06em;
  margin-top: 1px;
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

/* 分组标题 */
.nav-group-title {
  font-size: 11px;
  font-weight: 600;
  color: #A8B4AC;
  letter-spacing: 0.1em;
  padding: 12px 12px 6px;
  display: block;
}

/* 菜单项：圆角内凹块 */
.sidebar-menu .el-menu-item {
  margin: 2px 10px;
  height: 40px;
  line-height: 40px;
  border-radius: 9px;
  color: #5B6B63;
  font-size: 13.5px;
  transition: background-color 0.15s ease-out, color 0.15s ease-out;
}

.sidebar-menu .el-menu-item .el-icon {
  font-size: 17px;
  color: #8A9A91;
}

/* 激活项：主色浅底 + 主色文字 + 左侧指示条 */
.sidebar-menu .el-menu-item.is-active {
  background: #EAF6EF !important;
  color: #15803D !important;
  font-weight: 600;
  position: relative;
}

.sidebar-menu .el-menu-item.is-active .el-icon {
  color: #15803D;
}

.sidebar-menu .el-menu-item.is-active::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background-color: #16A34A;
}

.sidebar-menu .el-menu-item:hover {
  background-color: #F2F5F3 !important;
  color: #10231A;
}

.sidebar-menu .el-menu-item:hover .el-icon {
  color: #22A55A;
}

/* 子菜单「系统管理」标题 */
.sidebar-menu :deep(.el-sub-menu__title) {
  margin: 2px 10px;
  height: 40px;
  line-height: 40px;
  border-radius: 9px;
  color: #5B6B63;
  font-size: 13.5px;
  transition: background-color 0.15s ease-out, color 0.15s ease-out;
}

.sidebar-menu :deep(.el-sub-menu__title .el-icon) {
  font-size: 17px;
  color: #8A9A91;
}

.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background-color: #F2F5F3 !important;
  color: #10231A;
}

.sidebar-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #15803D;
  font-weight: 600;
}

.sidebar-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title .el-icon) {
  color: #15803D;
}

/* 子菜单展开容器与子项 */
.sidebar-menu :deep(.el-menu--inline) {
  background: transparent;
  padding-bottom: 4px;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item) {
  margin: 2px 10px 2px 30px;
  height: 38px;
  line-height: 38px;
  border-radius: 7px;
  color: #5B6B63;
  font-size: 13px;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item .el-icon) {
  font-size: 15px;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item:hover) {
  background-color: #F2F5F3 !important;
  color: #10231A;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item.is-active) {
  background: #EAF6EF !important;
  color: #15803D !important;
  font-weight: 600;
}

/* 折叠态：图标居中，隐藏文案（EP 折叠模式不自动隐藏 span，需手动） */
.sidebar-menu.el-menu--collapse .el-menu-item {
  margin: 4px 8px;
  padding: 0 !important;
  justify-content: center;
}

.sidebar-menu.el-menu--collapse .el-menu-item > span,
.sidebar-menu.el-menu--collapse .el-sub-menu__title > span {
  display: none;
}

.sidebar-menu.el-menu--collapse :deep(.el-sub-menu__title) {
  margin: 4px 8px;
  padding: 0 !important;
  justify-content: center;
}

.sidebar-menu.el-menu--collapse :deep(.el-menu-item-group__title) {
  display: none;
}

/* 顶栏管理员用户卡（原侧栏底部用户卡） */
.admin-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #15803D, #34C77B);
  color: #FFFFFF;
  font-size: 12.5px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 12.5px;
  font-weight: 600;
  color: #10231A;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 10.5px;
  color: #8A9A91;
  margin-top: 1px;
}

/* ── 顶栏 ── */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #E7ECE9;
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
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: #5B6B63;
  cursor: pointer;
  transition: background-color 0.15s ease-out, color 0.15s ease-out;
  flex-shrink: 0;
}

.collapse-btn:hover {
  background: #F2F5F3;
  color: #15803D;
}

.collapse-btn:focus-visible {
  outline: 2px solid #6EE7A8;
  outline-offset: 2px;
}

.breadcrumb {
  font-size: 13px;
  color: #8A9A91;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.breadcrumb-current {
  color: #10231A;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

/* 顶栏管理员用户卡 */
.header-user {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 6px 0 10px;
  border-radius: 12px;
  border: 1px solid #EDF1EE;
  background: #F6F9F7;
  white-space: nowrap;
  transition: border-color 0.15s ease-out, box-shadow 0.15s ease-out;
}

.header-user:hover {
  border-color: #DDE4DF;
  box-shadow: 0 2px 8px rgba(16, 24, 40, 0.06);
}

.user-logout {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #8A9A91;
  cursor: pointer;
  transition: background-color 0.15s ease-out, color 0.15s ease-out;
}

.user-logout:hover {
  background: #FEE9E9;
  color: #B91C1C;
}

.user-logout:focus-visible {
  outline: 2px solid #6EE7A8;
  outline-offset: 1px;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #5B6B63;
  cursor: pointer;
  position: relative;
  transition: background-color 0.15s ease-out, color 0.15s ease-out;
}

.icon-btn:hover {
  background: #F2F5F3;
  color: #15803D;
}

.dot {
  position: absolute;
  top: 6px;
  right: 6px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  border-radius: 7px;
  background: #DC2626;
  color: #fff;
  font-size: 10px;
  line-height: 14px;
  text-align: center;
  border: 1.5px solid #fff;
  box-sizing: border-box;
}

.notice-wrap {
  position: relative;
}

.notice-panel {
  position: absolute;
  top: 42px;
  right: 0;
  width: 240px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
  padding: 8px 0;
  z-index: 1000;
}

.notice-title {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #1A1B1C;
  border-bottom: 1px solid #F0F0F0;
}

.notice-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  font-size: 13px;
  color: #4B5563;
  cursor: pointer;
}

.notice-item:hover {
  background: #F6F8F7;
}

.notice-num {
  color: #DC2626;
  font-weight: 600;
}

/* ── 内容区 ── */
.main {
  background: #F6F8F7;
  padding: 24px;
  overflow-y: auto;
}

/* ── 响应式 ── */
@media (max-width: 767px) {
  .header {
    padding: 0 16px;
  }
  .main {
    padding: 16px;
  }
  .header-user .user-info {
    display: none;
  }
}
</style>
