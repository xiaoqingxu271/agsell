import type { Router, RouteMeta as RouterRouteMeta, RouteLocationRaw } from 'vue-router'
import { useAdminStore } from '@/stores/admin'

/** 路由元信息 */
export interface RouteMeta extends RouterRouteMeta {
  title?: string
  /** 无需登录即可访问 */
  public?: boolean
  /** 需要管理员登录 */
  requiresAdminAuth?: boolean
}

// ─── 所有路由预定义，守卫通过 meta 控制访问权限 ────────────────────────────────

export const allRoutes: import('vue-router').RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/admin/login',
    meta: { public: true },
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/admin/LoginView.vue'),
    meta: { title: '管理员登录', public: true },
  },
  {
    path: '/admin',
    name: 'AdminLayout',
    component: () => import('@/views/admin/LayoutView.vue'),
    redirect: '/admin/dashboard',
    meta: { requiresAdminAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '数据概览' },
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserListView.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'categories',
        name: 'AdminCategories',
        component: () => import('@/views/admin/CategoryListView.vue'),
        meta: { title: '分类管理' },
      },
      {
        path: 'products',
        name: 'AdminProducts',
        component: () => import('@/views/admin/ProductListView.vue'),
        meta: { title: '商品管理' },
      },
      {
        path: 'orders',
        name: 'AdminOrders',
        component: () => import('@/views/admin/OrderListView.vue'),
        meta: { title: '订单管理' },
      },
      {
        path: 'reviews',
        name: 'AdminReviews',
        component: () => import('@/views/admin/ReviewListView.vue'),
        meta: { title: '评价管理' },
      },
      {
        path: 'banners',
        name: 'AdminBanners',
        component: () => import('@/views/admin/BannerListView.vue'),
        meta: { title: '轮播图管理' },
      },
      {
        path: 'after-sales',
        name: 'AdminAfterSales',
        component: () => import('@/views/admin/AfterSalesListView.vue'),
        meta: { title: '售后管理' },
      },
      {
        path: 'traceability',
        name: 'AdminTraceability',
        component: () => import('@/views/admin/TraceabilityListView.vue'),
        meta: { title: '产地溯源' },
      },
      {
        path: 'seckill',
        name: 'AdminSeckill',
        component: () => import('@/views/admin/SeckillListView.vue'),
        meta: { title: '秒杀管理' },
      },
      {
        path: 'hot-word',
        name: 'AdminHotWord',
        component: () => import('@/views/admin/HotWordListView.vue'),
        meta: { title: '搜索热词' },
      },
      {
        path: 'system/admin',
        name: 'AdminSystemAdmin',
        component: () => import('@/views/admin/AdminListView.vue'),
        meta: { title: '管理员管理' },
      },
      {
        path: 'system/config',
        name: 'AdminSystemConfig',
        component: () => import('@/views/admin/SystemConfigView.vue'),
        meta: { title: '系统配置' },
      },
      {
        path: 'system/log',
        name: 'AdminSystemLog',
        component: () => import('@/views/admin/OperationLogView.vue'),
        meta: { title: '操作日志' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/admin/login',
    meta: { public: true },
  },
]

// ─── 守卫工具 ──────────────────────────────────────────────────────────────────

/** 角色 → 可访问的管理端路径（与 LayoutView 侧栏菜单权限矩阵一致） */
const roleAllowedPaths: Record<string, string[]> = {
  OPERATOR: ['/admin/dashboard', '/admin/categories', '/admin/products', '/admin/orders'],
}

/** 判断当前角色是否可访问指定管理端路径 */
function isPathAllowed(role: string, path: string): boolean {
  if (role === 'SUPER_ADMIN') return true
  if (role === 'ADMIN') return path.startsWith('/admin/') && !path.startsWith('/admin/system/')
  const allowed = roleAllowedPaths[role]
  if (!allowed) return false
  return allowed.some((p) => path === p || path.startsWith(p + '/'))
}

export function setTitle(meta: RouteMeta) {
  const title = meta.title ? `${meta.title} | 农产品销售系统` : '农产品销售系统'
  document.title = title
}

export async function handleAuthGuard(
  to: ReturnType<Router['resolve']>,
): Promise<boolean | RouteLocationRaw> {
  const meta = to.meta as RouteMeta

  if (meta.public) return true

  if (meta.requiresAdminAuth) {
    const adminStore = useAdminStore()
    const valid = await adminStore.fetchAdminInfo()
    if (!valid) return { path: '/admin/login', query: { redirect: to.fullPath } }
    // 当前角色无该模块权限 → 回到数据概览，避免进入无权限页面触发后端 40201
    if (!isPathAllowed(adminStore.adminInfo?.role ?? '', to.path)) {
      return '/admin/dashboard'
    }
    return true
  }

  return true
}

/** 登出时清除动态路由（静态路由模式下为空操作） */
export function clearDynamicRoutes() {}
