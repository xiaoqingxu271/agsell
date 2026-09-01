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
  replied: boolean
  isAnonymous: number
  createTime: string
}

// ─── 错误码枚举（与后端 ErrorCode 对齐）─────────────────────────────────────

export enum ErrorCode {
  SUCCESS = 0,
  PARAMS_ERROR = 40000,
  NOT_LOGIN_ERROR = 40100,
  NO_AUTH_ERROR = 40101,
  NOT_FOUND_ERROR = 40400,
  FORBIDDEN_ERROR = 40300,
  SYSTEM_ERROR = 50000,
  OPERATION_ERROR = 50001,
  ADMIN_NOT_LOGIN_ERROR = 40200,
  ADMIN_NO_AUTH_ERROR = 40201,
  PASSWORD_ERROR = 40301,
  USER_ALREADY_EXISTS = 40401,
  PHONE_ALREADY_EXISTS = 40402,
  SMS_CODE_INVALID = 40001,
  SMS_CODE_EXPIRED = 40002,
  SMS_CODE_SEND_TOO_FAST = 40003,
}
