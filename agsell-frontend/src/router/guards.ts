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
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/admin/login',
    meta: { public: true },
  },
]

// ─── 守卫工具 ──────────────────────────────────────────────────────────────────

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
    return true
  }

  return true
}

/** 登出时清除动态路由（静态路由模式下为空操作） */
export function clearDynamicRoutes() {}
