# agsell 管理后台 — 前端原型设计提示词

> 目标：为 Vue 3 + Element Plus 管理后台生成可用的原型代码，覆盖需求文档第 5 章（管理后台 Web）全部 P0 功能。
>
> 风格：扁平化企业后台，不追求花哨动效，以清晰、稳定、可操作为核心。
>
> 色彩基准：与现有 LayoutView.vue 保持一致（侧栏 `#304156`，主色 `#409eff`，背景 `#f0f2f5`）。

---

## 一、整体布局约定（所有页面共用）

采用已有 `LayoutView.vue` 的三段式布局，新增页面直接套用：

```
┌─────────────────────────────────────────────────────┐
│ 侧栏（宽200px，深色 #304156）                         │
│  ├─ Logo：🌾 农产品销售                               │
│  ├─ 数据概览（/admin/dashboard）                       │
│  ├─ 商品管理 → 展开子菜单                             │
│  │    ├─ 商品列表                                     │
│  │    └─ 分类管理                                     │
│  ├─ 订单管理                                         │
│  ├─ 用户管理                                         │
│  ├─ 评价管理                                         │
│  ├─ 轮播图管理                                       │
│  └─ 系统管理 → 展开子菜单                             │
│       ├─ 管理员管理                                   │
│       └─ 系统配置                                     │
├─────────────────────────────────────────────────────┤
│ 顶栏（白底，60px高）                                  │
│  左侧：页面标题                                        │
│  右侧：管理员名 + 退出按钮                              │
├─────────────────────────────────────────────────────┤
│ 内容区（#f0f2f5 背景，padding 20px）                   │
│  每个业务页面 = 一个 .page div                         │
└─────────────────────────────────────────────────────┘
```

**路由 meta 规范**，每个路由加上 `title` 字段供顶栏显示：

```ts
{ path: '/admin/products', meta: { title: '商品管理' } }
```

---

## 二、各页面原型设计

### 2.1 登录页（已有，补充细节）

**页面**：`views/admin/LoginView.vue`

现状基本可用，建议补充：
- 表单上方保留现有 logo 和标题
- 输入框用 `el-input` 的 `prefix-icon` 属性，用户名前加 `User` 图标，密码前加 `Lock` 图标（来自 `@element-plus/icons-vue`）
- 页面背景色 `#f5f7fa`，卡片圆角 8px，阴影 `0 2px 12px rgba(0,0,0,0.08)`

---

### 2.2 仪表盘（数据概览）

**页面**：`views/admin/DashboardView.vue`

#### 顶部统计卡片（el-row 4 列）

| 卡片 | 图标 | 标题 | 数值示例 | 颜色 |
|------|------|------|---------|------|
| 1 | `Order` | 今日订单数 | 23 | primary |
| 2 | `Coin` | 今日销售额 | ¥1,842.00 | success |
| 3 | `User` | 总用户数 | 1,280 | warning |
| 4 | `Goods` | 总商品数 | 356 | info |

每张卡片用 `el-card shadow="hover"`，内部 `el-statistic`，数字偏大（font-size 28px），标题小字灰色。

#### 中部两栏布局

左侧（占 16 列）：销售趋势折线图（ECharts）
- X 轴：近 7 天日期
- Y 轴：销售额（元）
- 曲线颜色 `#409eff`
- 标题：**近 7 日销售趋势**

右侧（占 8 列）：订单状态分布饼图
- 分类：待付款 / 待发货 / 待收货 / 已完成 / 已取消
- 颜色用 Element Plus 的 tag 颜色对应
- 标题：**订单状态分布**

#### 底部两栏布局

左侧（占 14 列）：热销商品排行（el-table，5 条）
- 列：排名、商品名称、销量、销售额
- 排名列用 `el-tag` 显示数字，第 1 名橙色，第 2 名灰色，第 3 名棕色，其余灰色

右侧（占 10 列）：最近订单（el-table，10 条）
- 列：订单号（截取后 6 位）、商品名（截取前 10 字）、金额、状态 tag
- 点击行跳转到订单详情页

---

### 2.3 商品列表

**页面**：`views/admin/ProductListView.vue`

#### 搜索区（el-card + el-form inline）

| 字段 | 组件 | 占宽 |
|------|------|------|
| 商品名称 | el-input，clearable | 180px |
| 所属分类 | el-select，级联选择或两级下拉 | 150px |
| 状态 | el-select（全部/上架/下架） | 120px |
| 操作 | 搜索按钮（primary）+ 重置按钮 | — |

搜索区右侧放 **新增商品** 按钮（type=primary，带 `Plus` 图标）。

#### 表格区（el-card）

| 列 | 宽度 | 内容 |
|----|------|------|
| 商品图片 | 80px | el-image，24×24 缩略图，preview-src-list 支持预览 |
| 商品名称 | min-width 150 | 超出不换行，省略号截断 |
| 分类 | 100px | 分类名称 |
| 价格 | 100px | 红色 `¥39.90`，原价用删除线灰色小字 |
| 库存 | 80px | 库存 < 10 时数字标红 |
| 销量 | 80px | 数字 |
| 状态 | 80px | el-tag：上架=success，下架=info |
| 操作 | 180px fixed right | 编辑 / 上下架切换 / 删除 |

#### 批量操作栏（表格下方，隐藏状态默认）

当勾选若干行时，底部出现 bar：
- 已选 N 件
- 批量上架（primary）
- 批量下架（warning）
- 批量删除（danger）

#### 分页

`el-pagination`，layout 用 `total, sizes, prev, pager, next, jumper`，pageSize 可选 10/20/50。

---

### 2.4 新增/编辑商品

**页面**：`views/admin/ProductEditView.vue`

使用 `el-dialog`（最大宽度 720px）或独立路由页面。推荐独立页面，路由 `/admin/products/edit/:id`（编辑）和 `/admin/products/add`（新增）。

表单分三块，用 `el-form` + `el-divider` 分隔：

#### 第一块：基本信息

| 字段 | 组件 | 说明 |
|------|------|------|
| 商品名称 | el-input | 必填，max 100 字 |
| 副标题 | el-input | 可选，max 200 字，placeholder="产地直发 坏果包赔" |
| 所属分类 | el-cascader | 级联选择，加载分类树 |
| 商品状态 | el-switch | 默认关闭（下架），开启=上架 |

#### 第二块：价格与库存

| 字段 | 组件 | 说明 |
|------|------|------|
| 售价 | el-input-number | 必填，最小单位 0.01，前缀 ¥ |
| 原价 | el-input-number | 可选，显示删除线提示 |
| 总库存 | el-input-number | 必填，>= 0 |
| 库存预警线 | el-input-number | 低于此值列表中标红，默认 10 |

#### 第三块：商品图片

使用 `el-upload` 的 `list-type="picture-card"` 多图上传：
- 第一张为主图（拖拽排序）
- 最多 9 张
- 上传前压缩（<= 5MB）
- 支持预览和删除

#### 第四块：商品详情

- `el-input` type=textarea，300px 高，或接入富文本编辑器（Quill，通过 `vue-quill-editor`）
- placeholder："请输入商品详情描述（支持图文）"

#### 第五块：农产品信息（特色字段）

| 字段 | 组件 | 说明 |
|------|------|------|
| 产地 | el-input | 如"江西赣州" |
| 采摘/上市日期 | el-date-picker | type=date |
| 保质期 | el-input | 如"30天" |
| 储存方式 | el-select | 选项：常温 / 冷藏 / 冷冻 / 阴凉干燥处 |

#### 底部操作

- 取消按钮
- 保存草稿（type=info）
- 确认发布（type=primary，默认下架状态保存；若状态开关打开则直接上架）

---

### 2.5 分类管理

**页面**：`views/admin/CategoryListView.vue`

#### 左侧：分类树（el-tree）

- 勾选模式关闭
- 节点显示：名称 + 状态 tag（绿色启用/灰色禁用）
- 右键菜单或工具栏按钮：新增一级分类 / 新增子分类
- 拖拽排序：`draggable` + `allow-drop`

#### 右侧：分类编辑面板（el-card）

选中节点后展示表单：
- 分类名称（必填）
- 图标：el-upload 单图上传（picture-card，最多 1 张，100×100）
- 父分类：el-select（一级分类 parent_id=0，可切换为"无"）
- 排序值：el-input-number
- 状态：el-switch
- 保存 / 取消

#### 顶部工具栏

- 新增一级分类（primary）
- 删除选中（danger，有商品关联时弹提示阻断）

---

### 2.6 订单列表

**页面**：`views/admin/OrderListView.vue`

#### 搜索区

| 字段 | 组件 | 占宽 |
|------|------|------|
| 订单号 | el-input | 180px |
| 用户昵称 | el-input | 150px |
| 订单状态 | el-select（全部/待付款/待发货/待收货/已完成/已取消/售后中） | 130px |
| 下单时间 | el-date-picker range | 240px |

#### 表格

| 列 | 宽度 | 内容 |
|----|------|------|
| 订单号 | 160px | 完整显示，可复制 |
| 用户 | 100px | 昵称 |
| 商品 | min-width 180 | 第一件商品名，超出省略 |
| 实付金额 | 100px | 红色加粗 |
| 状态 | 90px | el-tag（待付款=warning，待发货=primary，待收货=success，已完成=default，已取消=info，售后中=danger） |
| 下单时间 | 160px | — |
| 操作 | 140px fixed right | 查看 / 发货（仅待发货状态显示） |

#### 发货弹窗（el-dialog）

- 物流公司：el-input（手动输入）
- 物流单号：el-input
- 备注：el-input textarea，可选
- 提交发货 / 取消

---

### 2.7 订单详情

**页面**：`views/admin/OrderDetailView.vue`

布局：两栏，左侧信息，右侧操作。

#### 左侧：信息面板（el-card 堆叠）

**基本信息**
- 订单号
- 下单时间
- 支付时间
- 支付方式（el-tag：模拟支付=blue）

**商品清单**（el-table）
- 商品图、名称、规格、单价、数量、小计

**收货信息**
- 收件人、电话、省市区详细地址
- 用 el-descriptions 展示

**金额明细**
- 商品金额 / 运费 / 优惠 / 实付金额
- 实付金额红色加粗

#### 右侧：操作面板（el-card）

- 待发货：显示"发货"按钮（type=primary）
- 待收货：显示"确认收货"按钮（type=success）
- 已完成：显示"查看物流"（如有物流信息）
- 可取消：显示"关闭订单"（type=danger）

操作后弹 ElMessageBox 二次确认。

---

### 2.8 用户管理

**页面**：`views/admin/UserListView.vue`

现状已有基础框架，补充以下细节：

- 表格增加 **头像** 列（el-image，32×32 圆形）
- 状态列 el-switch 改为行内直接操作（保持现有逻辑）
- 操作列增加 **查看详情** 按钮，点击跳转用户详情页
- 搜索框补充手机号和昵称两个独立输入（或用合并搜索框，已有）

**用户详情弹窗**（el-dialog）：
- 用户信息：头像、昵称、手机号、注册时间、最后登录时间/IP
- 下单记录：el-table 展示最近 10 条订单（订单号、金额、状态、时间）
- 底部操作：禁用/启用（danger/success）、删除（P2，可选）

---

### 2.9 评价管理

**页面**：`views/admin/ReviewListView.vue`

#### 搜索区

| 字段 | 组件 |
|------|------|
| 商品名称 | el-input |
| 用户昵称 | el-input |
| 评分 | el-select（全部/1星/2星/3星/4星/5星）|

#### 表格

| 列 | 宽度 | 内容 |
|----|------|------|
| 用户 | 100px | 头像 + 昵称 |
| 商品 | min-width 150 | 商品名（省略） |
| 评分 | 100px | 5 星 el-rate，只读，disabled |
| 评价内容 | min-width 250 | 截断 2 行，超出省略 |
| 评价图片 | 120px | 缩略图行，最多 3 张预览 |
| 回复 | 120px | 已回复=绿色 check，未回复=灰色 — |
| 时间 | 160px | — |
| 操作 | 140px fixed right | 回复 / 删除 |

#### 回复弹窗（el-dialog）

- 评价内容只读展示
- 评价图片只读预览
- 回复输入框：el-input textarea，max 500 字，显示字数统计
- 提交回复 / 取消

---

### 2.10 轮播图管理

**页面**：`views/admin/BannerListView.vue`

#### 表格

| 列 | 宽度 | 内容 |
|----|------|------|
| 图片 | 120px | el-image 80×40，fit="cover" |
| 标题 | 150px | — |
| 跳转链接 | 200px | 截断显示，可复制 |
| 排序 | 80px | el-input-number 行内编辑 |
| 状态 | 80px | el-switch 行内切换 |
| 操作 | 120px fixed right | 编辑 / 删除 |

#### 新增/编辑弹窗

- 图片上传：el-upload，limit=1，list-type=picture
- 标题：el-input，max 100 字
- 跳转类型：el-radio（商品详情页 / 外部链接 / 无）
- 跳转值：根据类型显示 el-select（商品）或 el-input（URL）
- 排序：el-input-number
- 启用状态：el-switch

---

### 2.11 管理员管理

**页面**：`views/admin/AdminListView.vue`

#### 表格

| 列 | 宽度 | 内容 |
|----|------|------|
| ID | 80px | — |
| 用户名 | 120px | — |
| 真实姓名 | 120px | — |
| 角色 | 100px | el-tag（ADMIN=orange，OPERATOR=blue） |
| 状态 | 80px | el-tag（正常=success，禁用=danger） |
| 最后登录 | 160px | — |
| 操作 | 160px fixed right | 编辑 / 删除 |

#### 新增/编辑弹窗

- 用户名：el-input，新增时必填，编辑时只读
- 密码：新增必填，编辑时选填（不填则不修改）
- 真实姓名：el-input
- 角色：el-select（ADMIN / OPERATOR）
- 状态：el-switch

> 注意：不可删除当前登录管理员账号，删除前做判断并提示。

---

### 2.12 系统配置

**页面**：`views/admin/SystemConfigView.vue`

使用 `el-tabs` 分两个 tab：

#### Tab 1：基础设置

| 字段 | 组件 |
|------|------|
| 平台名称 | el-input |
| 客服电话 | el-input |
| 平台简介 | el-input textarea，200px 高 |

#### Tab 2：运费设置

| 字段 | 组件 |
|------|------|
| 默认运费（元） | el-input-number |
| 满额包邮阈值（元） | el-input-number，0=不包邮 |

底部统一一个 **保存** 按钮（type=primary，全宽）。

---

## 三、组件复用约定

以下组件在各页面中重复出现，建议抽取为公共组件：

| 组件名 | 路径 | 说明 |
|--------|------|------|
| `SearchBar.vue` | `components/common/SearchBar.vue` | 接收 fields prop，渲染搜索表单 + 操作按钮 |
| `StatusTag.vue` | `components/common/StatusTag.vue` | 统一状态 tag，根据 value 自动映射颜色 |
| `ImageUpload.vue` | `components/common/ImageUpload.vue` | 封装 el-upload，支持多图/单图，返回 url 数组 |

---

## 四、Element Plus 组件使用指引

| 场景 | 推荐组件 | 关键 props |
|------|---------|-----------|
| 列表筛选 | el-form + el-input + el-select + el-date-picker | inline, label-width="60" |
| 数据表格 | el-table + el-table-column | stripe, border, fixed, show-overflow-tooltip |
| 分页 | el-pagination | layout="total, sizes, prev, pager, next, jumper" |
| 弹窗表单 | el-dialog | draggable, close-on-click-modal=false |
| 状态标识 | el-tag | type="success\|warning\|danger\|info\|primary" |
| 行内开关 | el-switch | active-text, inactive-text |
| 确认弹窗 | ElMessageBox.confirm | type="warning", confirmButtonText |
| 提示消息 | ElMessage | type="success\|warning\|error\|info" |
| 图片预览 | el-image | preview-src-list, fit="cover" |
| 树形分类 | el-tree | draggable, show-checkbox=false |
| 统计卡片 | el-card + el-statistic | shadow="hover" |
| 图表 | ECharts（echarts 库） | 不用 Element 自带图表 |

---

## 五、交互细节要求

1. **加载状态**：所有接口请求期间，表格用 `v-loading`，按钮用 `:loading`，禁止重复提交。
2. **删除操作**：所有删除操作必须先弹 `ElMessageBox.confirm`，二次确认后再请求接口。
3. **表单校验**：必填字段用 `el-form` 的 `rules` 做前端校验，错误提示用 `message="xxx不能为空"`。
4. **空状态**：表格数据为空时，用 `el-empty` 展示"暂无数据"。
5. **操作成功**：增删改成功后调用 `ElMessage.success('操作成功')`，失败调用 `ElMessage.error`。
6. **时间格式化**：统一用 `dayjs` 格式化，格式 `YYYY-MM-DD HH:mm:ss`。
7. **价格展示**：统一两位小数，前缀 `¥`，红色 `#f56c6c`。

---

## 六、文件结构目标

生成后应落盘在以下路径：

```
agsell-frontend/src/views/admin/
├── LoginView.vue          ← 已有，可选增强
├── LayoutView.vue         ← 已有，需补充菜单项
├── DashboardView.vue      ← 已有，需补充图表
├── UserListView.vue       ← 已有，需补充头像列和详情弹窗
├── ProductListView.vue    ← 新建
├── ProductEditView.vue    ← 新建
├── CategoryListView.vue   ← 新建
├── OrderListView.vue      ← 新建
├── OrderDetailView.vue    ← 新建
├── ReviewListView.vue     ← 新建
├── BannerListView.vue     ← 新建
├── AdminListView.vue      ← 新建
└── SystemConfigView.vue   ← 新建

agsell-frontend/src/router/index.ts   ← 补充所有路由
agsell-frontend/src/router/guards.ts  ← 按需补充
```

---

## 七、生成顺序建议

按以下顺序生成，每次完成后验证路由是否正常跳转：

1. `LayoutView.vue` — 补充侧边栏完整菜单（含商品/订单/分类/评价/轮播图/系统子菜单）
2. `ProductListView.vue` + `ProductEditView.vue`
3. `CategoryListView.vue`
4. `OrderListView.vue` + `OrderDetailView.vue`
5. `DashboardView.vue` — 补充图表
6. `UserListView.vue` — 补充头像和详情弹窗
7. `ReviewListView.vue`
8. `BannerListView.vue`
9. `AdminListView.vue`
10. `SystemConfigView.vue`

每生成一个页面后，在 `router/index.ts` 中注册路由，确保侧栏菜单 index 与路由 path 对应。
