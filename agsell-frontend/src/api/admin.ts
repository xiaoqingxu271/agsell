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
  AdminAfterSalesListItemVO,
  AdminAfterSalesDetailVO,
  AfterSalesHandleRequest,
  TraceabilityListItemVO,
  TraceabilityDetailVO,
  TraceabilityCreateRequest,
  TraceabilityUpdateRequest,
  TraceabilityCreateResultVO,
  ProductionRecordRequest,
  ProductionRecordVO,
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

// ─── 售后 ─────────────────────────────────────────────────────────────────────

/** 售后单列表 */
export function listAfterSales(params: {
  pageNum?: number
  pageSize?: number
  status?: number | null
  afterSalesNo?: string
  username?: string
}) {
  return request.get<Page<AdminAfterSalesListItemVO>>('/admin/after-sales/list', { params })
}

/** 售后单详情 */
export function getAfterSalesDetail(afterSalesNo: string) {
  return request.get<AdminAfterSalesDetailVO>(`/admin/after-sales/${afterSalesNo}`)
}

/** 处理售后（同意退款/拒绝） */
export function handleAfterSales(afterSalesNo: string, data: AfterSalesHandleRequest) {
  return request.post<null>(`/admin/after-sales/${afterSalesNo}/handle`, data)
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

// ─── 产地溯源 ────────────────────────────────────────────────────────────────

/** 溯源信息分页查询 */
export function listTraceability(params: {
  pageNum?: number
  pageSize?: number
  productName?: string
  batchNo?: string
}) {
  return request.get<Page<TraceabilityListItemVO>>('/admin/traceability/page', { params })
}

/** 溯源信息详情（含生产记录） */
export function getTraceabilityDetail(id: number) {
  return request.get<TraceabilityDetailVO>(`/admin/traceability/detail/${id}`)
}

/** 新增溯源信息 */
export function createTraceability(data: TraceabilityCreateRequest) {
  return request.post<TraceabilityCreateResultVO>('/admin/traceability', data)
}

/** 编辑溯源信息 */
export function updateTraceability(id: number, data: TraceabilityUpdateRequest) {
  return request.put<null>(`/admin/traceability/${id}`, data)
}

/** 删除溯源信息（级联删除生产记录） */
export function deleteTraceability(id: number) {
  return request.delete<null>(`/admin/traceability/${id}`)
}

/** 生成溯源二维码 */
export function generateTraceQr(id: number) {
  return request.post<{ qrCodeUrl: string }>(`/admin/traceability/${id}/generate-qr`)
}

/** 生产记录列表 */
export function listTraceRecords(id: number) {
  return request.get<ProductionRecordVO[]>(`/admin/traceability/${id}/records`)
}

/** 新增生产记录 */
export function createTraceRecord(data: ProductionRecordRequest) {
  return request.post<{ id: number }>('/admin/traceability/record', data)
}

/** 编辑生产记录 */
export function updateTraceRecord(id: number, data: ProductionRecordRequest) {
  return request.put<null>(`/admin/traceability/record/${id}`, data)
}

/** 删除生产记录 */
export function deleteTraceRecord(id: number) {
  return request.delete<null>(`/admin/traceability/record/${id}`)
}
