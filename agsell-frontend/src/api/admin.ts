import request from '@/utils/request'
import type {
  AdminInfoVO,
  AdminLoginRequest,
  AdminLoginVO,
  AdminUserListItemVO,
  AdminUserStatusRequest,
  Page,
  CategoryListItemVO,
  CategoryTreeVO,
  CategoryCreateRequest,
  ProductListItemVO,
  ProductQueryRequest,
  ProductCreateRequest,
  AdminOrderListItemVO,
  AdminOrderDetailVO,
  OrderShipRequest,
  ReviewListItemVO,
  ReplyRequest,
  AdminStatisticsVO,
  StatisticsTrendVO,
} from '@/types'

/** 管理员登录 */
export function adminLogin(data: AdminLoginRequest) {
  return request.post<AdminLoginVO>('/admin/login', data)
}

/** 获取当前管理员信息 */
export function getAdminInfo() {
  return request.get<AdminInfoVO>('/admin/info')
}

/** 管理员退出登录 */
export function adminLogout() {
  return request.post<null>('/admin/logout')
}

/** 用户列表 */
export function listUsers(params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
}) {
  return request.get<Page<AdminUserListItemVO>>('/admin/user/list', { params })
}

/** 禁用/启用用户 */
export function updateUserStatus(id: number, data: AdminUserStatusRequest) {
  return request.put<null>(`/admin/user/${id}/status`, data)
}

// ─── 商品分类 ─────────────────────────────────────────────────────────────────

/** 获取分类树 */
export function getCategoryTree() {
  return request.get<CategoryTreeVO[]>('/admin/category/tree')
}

/** 获取分类列表（平铺） */
export function listCategoryItems() {
  return request.get<CategoryListItemVO[]>('/admin/category/list')
}

/** 新增/编辑分类 */
export function saveOrUpdateCategory(data: CategoryCreateRequest) {
  return request.post<null>('/admin/category', data)
}

/** 删除分类 */
export function deleteCategory(id: number) {
  return request.delete<null>(`/admin/category/${id}`)
}

/** 启用/禁用分类 */
export function updateCategoryStatus(id: number, status: number) {
  return request.put<null>(`/admin/category/${id}/status`, null, {
    params: { status },
  })
}

// ─── 商品 ─────────────────────────────────────────────────────────────────────

/** 商品列表（分页+筛选） */
export function listProducts(params: ProductQueryRequest) {
  return request.get<Page<ProductListItemVO>>('/admin/product/list', { params })
}

/** 新增/编辑商品 */
export function saveOrUpdateProduct(data: ProductCreateRequest) {
  return request.post<null>('/admin/product', data)
}

/** 删除商品 */
export function deleteProduct(id: number) {
  return request.delete<null>(`/admin/product/${id}`)
}

/** 上架/下架商品 */
export function updateProductStatus(id: number, status: number) {
  return request.put<null>(`/admin/product/${id}/status`, null, {
    params: { status },
  })
}

// ─── 订单 ─────────────────────────────────────────────────────────────────────

/** 订单列表（分页+筛选） */
export function listOrders(params: {
  pageNum?: number
  pageSize?: number
  status?: number | null
  orderNo?: string
  username?: string
}) {
  return request.get<Page<AdminOrderListItemVO>>('/admin/order/list', { params })
}

/** 订单详情 */
export function getOrderDetail(orderNo: string) {
  return request.get<AdminOrderDetailVO>(`/admin/order/${orderNo}`)
}

/** 发货 */
export function shipOrder(orderNo: string, data: OrderShipRequest) {
  return request.post<null>(`/admin/order/${orderNo}/ship`, data)
}

// ─── 轮播图 ────────────────────────────────────────────────────────────────────

/** 轮播图列表（管理端） */
export function listAllBanners() {
  return request.get<any[]>('/admin/banner/list')
}

/** 新增轮播图 */
export function createBanner(data: any) {
  return request.post<null>('/admin/banner', data)
}

/** 更新轮播图 */
export function updateBanner(id: number, data: any) {
  return request.put<null>(`/admin/banner/${id}`, data)
}

/** 删除轮播图 */
export function deleteBanner(id: number) {
  return request.delete<null>(`/admin/banner/${id}`)
}

// ─── 评价 ─────────────────────────────────────────────────────────────────────

/** 评价列表 */
export function listReviews(params: {
  pageNum?: number
  pageSize?: number
  productId?: number
  replied?: boolean | null
}) {
  return request.get<Page<ReviewListItemVO>>('/admin/review/list', { params })
}

/** 回复评价 */
export function replyReview(id: number, data: ReplyRequest) {
  return request.post<null>(`/admin/review/${id}/reply`, data)
}

/** 删除评价 */
export function deleteReview(id: number) {
  return request.delete<null>(`/admin/review/${id}`)
}

// ─── 数据统计 ─────────────────────────────────────────────────────────────────

/** 数据概览统计 */
export function getAdminStatistics() {
  return request.get<AdminStatisticsVO>('/admin/statistics/overview')
}

/** 数据趋势统计（近 N 天新增用户/订单数/销售额） */
export function getStatisticsTrend(days = 7) {
  return request.get<StatisticsTrendVO>('/admin/statistics/trend', {
    params: { days },
  })
}
