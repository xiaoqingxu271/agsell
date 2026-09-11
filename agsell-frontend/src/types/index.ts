/** 后端统一响应体 */
export interface BaseResponse<T = unknown> {
  code: number
  data: T
  message: string
}

/** 分页数据 */
export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// ─── 管理员相关类型 ──────────────────────────────────────────────────────────

export interface AdminLoginRequest {
  username: string
  password: string
}

export interface AdminLoginVO {
  token: string
  adminId: number
  realName: string
  role: string
}

export interface AdminInfoVO {
  adminId: number
  realName: string
  role: string
}

export interface AdminUserStatusRequest {
  /** 0=禁用 1=启用 */
  status: number
}

export interface AdminUserListItemVO {
  id: number
  username: string
  nickname: string
  avatar: string | null
  phone: string | null
  status: number
  loginTime: string | null
  createTime: string
}

// ─── 商品分类相关类型 ───────────────────────────────────────────────────────────

export interface CategoryListItemVO {
  id: number
  name: string
  icon: string | null
  parentId: number
  sort: number
  status: number
  createTime: string
}

export interface CategoryTreeVO extends Omit<CategoryListItemVO, 'children'> {
  children: CategoryTreeVO[]
}

export interface CategoryCreateRequest {
  id?: number
  name: string
  icon?: string
  parentId?: number
  sort?: number
}

// ─── 商品相关类型 ──────────────────────────────────────────────────────────────

export interface ProductSpecDTO {
  id?: number
  specName: string
  price: number
  stock: number
  image?: string
}

export interface ProductListItemVO {
  id: number
  name: string
  subtitle: string | null
  categoryId: number
  categoryName: string
  price: number
  originalPrice: number | null
  stock: number
  sales: number
  mainImage: string | null
  status: number
  createTime: string
}

export interface ProductQueryRequest {
  name?: string
  categoryId?: number
  status?: number | null
  pageNum?: number
  pageSize?: number
}

export interface ProductCreateRequest {
  id?: number
  name: string
  subtitle?: string
  categoryId: number
  price: number
  originalPrice?: number
  stock: number
  mainImage?: string
  images?: string
  description?: string
  origin?: string
  harvestDate?: string
  shelfLife?: string
  storage?: string
  status?: number
  sort?: number
  specs?: ProductSpecDTO[]
}

// ─── 订单相关类型 ──────────────────────────────────────────────────────────────

export interface OrderShipRequest {
  logType: string
  logNo: string
}

export interface AdminOrderListItemVO {
  id: number
  orderNo: string
  userId: number
  username: string | null
  totalAmount: number
  payAmount: number
  status: number
  statusText: string
  receiver: string
  phone: string
  itemCount: number
  createTime: string
}

export interface OrderItemVO {
  productId: number
  productName: string
  productImage: string | null
  specName: string
  price: number
  quantity: number
  subtotal: number
}

export interface AdminOrderDetailVO {
  orderNo: string
  userId: number
  username: string | null
  nickname: string | null
  totalAmount: number
  payAmount: number
  freight: number
  discount: number
  status: number
  statusText: string
  receiver: string
  phone: string
  address: string
  remark: string | null
  createTime: string
  payTime: string | null
  deliveryTime: string | null
  receiveTime: string | null
  logType: string | null
  logNo: string | null
  cancelReason: string | null
  items: OrderItemVO[]
}

// ─── 评价相关类型 ──────────────────────────────────────────────────────────────

export interface ReplyRequest {
  replyContent: string
}

export interface ReviewListItemVO {
  id: number
  orderId: number
  productId: number
  productName: string
  userId: number
  userName: string | null
  rating: number
  content: string
  replyContent: string | null
  replied: boolean
  isAnonymous: number
  createTime: string
}

// ─── 数据统计 ─────────────────────────────────────────────────────────────────

export interface AdminStatisticsVO {
  userTotal: number
  todayNewUsers: number
  activeTodayUsers: number
  disabledUsers: number
  productTotal: number
  onSaleProducts: number
  orderTotal: number
  paidOrders: number
  pendingShipOrders: number
  totalSales: number
}

/** 数据趋势统计（近 N 天） */
export interface StatisticsTrendVO {
  /** 日期列表（yyyy-MM-dd，升序） */
  dates: string[]
  /** 每日新增用户数 */
  newUsers: number[]
  /** 每日订单数 */
  orderCounts: number[]
  /** 每日销售额 */
  sales: number[]
}

// ─── 售后相关类型 ─────────────────────────────────────────────────────────────

export interface AdminAfterSalesListItemVO {
  id: number
  afterSalesNo: string
  orderNo: string
  userId: number
  username: string | null
  type: number
  typeText: string
  reasonType: string
  reasonTypeText: string
  reason: string | null
  refundAmount: number
  status: number
  statusText: string
  createTime: string
  handleTime: string | null
}

export interface AdminAfterSalesDetailVO extends AdminAfterSalesListItemVO {
  nickname: string | null
  orderPayAmount: number
  receiver: string
  phone: string
  address: string
  originalStatus: number
  originalStatusText: string
  images: string[]
  handleRemark: string | null
  handleBy: number | null
  items: OrderItemVO[]
}

export interface AfterSalesHandleRequest {
  /** true=同意退款 false=拒绝 */
  agree: boolean
  remark?: string
}

// ─── 产地溯源相关类型 ─────────────────────────────────────────────────────────

/** 认证类型 */
export type CertificationType = 'ORGANIC' | 'GREEN' | 'GEOGRAPHICAL' | 'NONE'

/** 生产记录类型 */
export type RecordType =
  | 'SEEDING'
  | 'FERTILIZING'
  | 'WATERING'
  | 'PEST_CONTROL'
  | 'HARVEST'
  | 'OTHER'

export interface ProductionRecordRequest {
  traceabilityId?: number
  recordType: RecordType
  recordDate: string
  content: string
  images?: string[]
  operator?: string
}

export interface ProductionRecordVO {
  id: number
  recordType: string
  recordTypeText: string
  recordDate: string
  content: string
  images: string[]
  operator: string | null
  createTime: string
}

export interface TraceabilityListItemVO {
  id: number
  productId: number
  productName: string
  productImage: string | null
  batchNo: string
  farmerName: string
  origin: string
  harvestDate: string | null
  certificationType: string | null
  certificationTypeText: string | null
  qrCodeUrl: string | null
  createTime: string
}

export interface TraceabilityDetailVO {
  id: number
  productId: number
  productName: string
  productImage: string | null
  batchNo: string
  farmerName: string
  farmerPhone: string | null
  originProvince: string | null
  originCity: string | null
  originDistrict: string | null
  plantingDate: string | null
  harvestDate: string | null
  qualityCheckResult: string | null
  pesticideTest: string | null
  certificationType: string | null
  certificationTypeText: string | null
  certificationUrls: string[]
  qrCodeUrl: string | null
  productionRecords: ProductionRecordVO[]
  createTime: string
}

export interface TraceabilityCreateRequest {
  productId?: number
  farmerName: string
  farmerPhone?: string
  originProvince?: string
  originCity?: string
  originDistrict?: string
  plantingDate?: string
  harvestDate?: string
  qualityCheckResult?: string
  pesticideTest?: string
  certificationType?: CertificationType | ''
  certificationUrls?: string[]
}

export interface TraceabilityUpdateRequest extends TraceabilityCreateRequest {}

export interface TraceabilityCreateResultVO {
  id: number
  batchNo: string
}
