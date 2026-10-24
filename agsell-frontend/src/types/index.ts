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

export interface ProductDetailVO {
  id: number
  name: string
  subtitle: string | null
  categoryName: string | null
  categoryId: number
  price: number
  originalPrice: number | null
  stock: number
  sales: number
  mainImage: string | null
  images: string[]
  description: string | null
  origin: string | null
  harvestDate: string | null
  shelfLife: string | null
  storage: string | null
  status: number
  specs: ProductSpecDTO[]
  hasTrace: boolean
  traceBatchNo: string | null
  /** 溯源产地（省+市+区县拼接，用于商品编辑回显） */
  traceOrigin: string | null
  createTime: string
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

export interface SentimentStatsVO {
  total: number
  positiveCount: number
  neutralCount: number
  negativeCount: number
  positiveRate: number
  topKeywords: string[]
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

  /** 情感标签 1=好评 0=中评 -1=差评（NULL=未分析） */
  sentimentLabel?: number
}

export interface AiReviewReplyVO {
  reviewId: number
  /** AI 生成的回复草稿（可编辑后再提交） */
  reply: string
  /** llm = Agnes 生成；fallback = 规则模板兜底（AI 服务不可用时） */
  source: string
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

export type TraceabilityUpdateRequest = TraceabilityCreateRequest

/** 轮播图（管理端） */
export type Banner = {
  id?: number
  title: string
  image: string
  link?: string
  sort: number
  status: number
}

export interface TraceabilityCreateResultVO {
  id: number
  batchNo: string
}


// ─── 秒杀相关类型 ─────────────────────────────────────────────────────────

export interface SeckillActivityVO {
  id: number
  activityCode: string
  productId: number
  productSpecId: number | null
  productName: string | null
  productImage: string | null
  productPrice: string | null
  seckillPrice: string
  seckillStock: number
  remainingStock: number
  seckillLimit: number
  startTime: string
  endTime: string
  status: number
  activityStatusText: string
  sort: number
  createTime: string
}

export interface SeckillActivityRequest {
  productId?: number
  productSpecId?: number | null
  seckillPrice?: number
  seckillStock?: number
  seckillLimit?: number
  startTime?: string
  endTime?: string
  sort?: number
  status?: number
}

// ─── 优惠券相关类型 ─────────────────────────────────────────────────────────

export interface CouponVO {
  id: number
  couponName: string
  couponType: number
  threshold: string
  amount: string
  discount: string | null
  maxDiscount: string | null
  productIds: number[] | null
  totalCount: number
  receivedCount: number
  perUserLimit: number
  validDays: number
  startTime: string | null
  endTime: string | null
  status: number
  sort: number
  createTime: string
}

export interface CouponRequest {
  couponName?: string
  couponType?: number
  threshold?: number
  amount?: number
  discount?: number | null
  maxDiscount?: number | null
  productIds?: number[]
  totalCount?: number
  perUserLimit?: number
  validDays?: number
  startTime?: string | null
  endTime?: string | null
  sort?: number
  status?: number
}

// ─── 搜索热词 ────────────────────────────────────────────────────────────────

export interface SearchHotWord {
  id: number
  word: string
  searchCount: number
  sort: number
  status: number
  isManual: number
  createTime?: string
  updateTime?: string
}

export interface SearchHotWordRequest {
  id?: number
  word?: string
  sort?: number
  status?: number
}

// ─── 系统管理（管理员/配置/日志） ────────────────────────────────────────────────

export interface AdminListItemVO {
  id: number
  username: string
  realName: string | null
  role: 'SUPER_ADMIN' | 'ADMIN' | 'OPERATOR'
  status: number
  loginIp: string | null
  loginTime: string | null
  createTime: string
}

export interface AdminCreateRequest {
  username: string
  password: string
  realName?: string
  role: 'ADMIN' | 'OPERATOR'
}

export interface AdminUpdateRequest {
  username?: string
  realName?: string
  role?: 'ADMIN' | 'OPERATOR'
}

export interface ConfigItemVO {
  configKey: string
  configValue: string
  description: string
}

export interface ConfigUpdateRequest {
  items: Array<{ configKey: string; configValue: string }>
}

export interface SysLogVO {
  id: number
  adminId: number
  adminName: string
  module: string
  action: string
  content: string | null
  ip: string
  createTime: string
}

/** 操作日志 keyset 游标分页响应 */
export interface SysLogPageVO {
  records: SysLogVO[]
  /** 总条数（后端 Long 序列化为字符串，前端需 Number() 转换） */
  total: number
  /** 是否还有下一页 */
  hasMore: boolean
  /** 下一页游标：本页最后一条 createTime */
  cursorTime: string | null
  /** 下一页游标：本页最后一条 id */
  cursorId: number | null
}

// ─── AI 知识库（RAG 维护页） ──────────────────────────────────────────────────

/**
 * Long/long 在本项目被 Jackson 统一序列化成字符串（避免 JS 精度丢失），
 * 所以 id 与 total 的声明是 string | number，用之前一律 Number() 归一。
 */
export type IdOrNum = string | number

/** FAQ 条目：字段与 ai-service 的 /v1/admin/kb/faqs 对齐（Java 代理已做驼峰映射） */
export interface AiFaqVO {
  id: IdOrNum
  question: string
  answer: string
  category: string
  keywords: string | null
  status: number
  updatedAt: string | null
}

/** FAQ 分页结果（Python 口径是 {total, items}，不是 MyBatis-Plus 的 records） */
export interface AiFaqPageVO {
  total: IdOrNum
  items: AiFaqVO[]
}

/** 新增/编辑 FAQ 的请求体 */
export interface AiFaqRequest {
  id?: number
  question?: string
  answer?: string
  category?: string
  keywords?: string
  status?: number
}

/**
 * 新增 FAQ 的结果。indexed 是"向量索引同步是否成功"，不是"这条在不在索引里"
 * （停用除名成功也返回 true）；false 时条目已在 MySQL 但 AI 还按旧知识库回答，
 * 页面必须提示点「重建 FAQ 索引」，不能只弹一句"新增成功"。
 */
export interface AiFaqCreateVO {
  id: IdOrNum
  indexed: boolean
}

/**
 * 知识库与索引状态。三个数分列是刻意的：只看 faqCount 会以为"知识都入库了"，
 * 真正决定 AI 能不能答的是 indexedCount；productIndexedCount 落后于
 * onSaleProductCount 就是"上了新品但 AI 召不到"。
 */
export interface AiKbStatsVO {
  faqCount: IdOrNum
  indexedCount: IdOrNum
  productIndexedCount: IdOrNum
  onSaleProductCount: IdOrNum
  embeddingModel: string | null
  topK: number | null
  productTopK: number | null
  /** 同步判断由 Java 侧算好后下发，前端不再自己比大小（避免字符串比较出错） */
  faqSynced: boolean
  productIndexSynced: boolean
  /** 近 7 日被 AI 引用的知识 Top（Python kb_hit_log 聚合）；null = 无日志数据 */
  recentHitTop: { faqId: IdOrNum; question: string; hits: number }[] | null
}

/** AI 转人工工单：Python 触发 escalate_human 后经内部接口落库，管理端查看并处理 */
export interface AdminAiTicketVO {
  id: IdOrNum
  userId: IdOrNum | null
  sessionId: string
  question: string
  reason: string | null
  status: number
  statusText: string
  handleRemark: string | null
  handleBy: IdOrNum | null
  handleTime: string | null
  createTime: string | null
}

/** 工单分页（MyBatis-Plus 口径 records/total） */
export interface AiTicketPageVO {
  records: AdminAiTicketVO[]
  total: IdOrNum
  size: number
  current: number
}

/** 工单处理请求体 */
export interface AiTicketHandleRequest {
  remark?: string
}

/** 近 N 日 AI 运营汇总（Python chat_log 聚合，Java 透传；无数据为全零结构） */
export interface AiOpsSummaryVO {
  days: number
  totalTurns: number
  refusedTurns: number
  refusalRate: number
  byTool: Record<string, number> | null
  daily: { date: string; count: number }[]
}

/** 首页公告条目（管理端 CRUD + 小程序公告条） */
export interface SysNoticeVO {
  id: IdOrNum | undefined
  title: string
  content?: string | null
  sort?: number
  status?: number
  createTime?: string | null
  updateTime?: string | null
}
