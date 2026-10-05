# agsell 管理端设计系统 v4.0（青翠绿 × 丰收金 × 中性底）

> 生效日期：2026-09-18 · 本文档为管理端页面样式**唯一权威来源**（取代 `design-system-v3.md`）。
> 适用范围：**管理端** `agsell-frontend`（Vue 3 + Element Plus 2.x + ECharts）。小程序端 `agsell-uniapp` 不在此范围。
> 落地形式：高保真设计稿（`doc/design-mockup-v4/`，5 页 1440px）+ 代码 token 层（`src/styles/theme.css`、`src/styles/admin-table.css`）+ 共享组件/页面重写。
> 约束：本次改造**只改设计不改逻辑**——所有接口调用、路由、状态、事件处理均未变动；`git diff` 应仅涉及模板/样式/共享组件/目录整理。

---

## 0. 设计判定

定位为**农业电商运营控制台（B2B operations console）**：青翠绿承载品牌与主操作，丰收金点亮金额与重点数据，中性暖灰底保证数据密度与阅读舒适度。

三旋钮：`DESIGN_VARIANCE: 4`（克制有序）· `MOTION_INTENSITY: 3`（150–250ms 轻反馈）· `VISUAL_DENSITY: 6`（数据密集但清晰）。

---

## 1. 设计原则

1. **品牌绿 = 关键操作与激活态**，禁止大面积铺色；**丰收金 = 金额与关键数字**；中性底承载内容。
2. **层次感**：页面标题 22px/700 → 卡片标题 15px/700 → 正文 13.5px → 辅助 12px；卡片圆角 14–16px、微阴影，hover 抬升。
3. **重点突出**：指标带采用「渐变主卡 + 独立次卡」制式，主卡承载全局核心数字（销售总额），次卡承载分项。
4. **统一和谐**：全部颜色/字号/间距走 token；列表页共用全局 class（`admin-table`/`admin-search-card`/`admin-card-title`/`admin-status-tag`/`admin-price`/`admin-pagination`），改 token 即全局生效。
5. **及时反馈**：按钮/菜单/卡片 hover 150–250ms ease-out；聚焦显示可见 focus ring（`#6EE7A8`）。
6. **可访问性**：正文对比度 ≥ 4.5:1；状态标签「颜色 + 文字」双通道。

---

## 2. 色彩系统

### 2.1 品牌青翠绿

| Token | Hex | 用途 |
|---|---|---|
| `--brand-900` | `#0E3B25` | 主卡渐变深端 |
| `--brand-700` | `#166534` | 主按钮 hover |
| `--brand-600` | `#15803D` | **品牌主色**：主按钮、链接、激活态、图表主系列 |
| `--brand-500` | `#16A34A` | 侧栏激活指示条 |
| `--brand-400` | `#22A55A` | 渐变过渡、次要系列 |
| `--brand-300` | `#34C77B` | 渐变浅端、成功、图表系列 |
| `--brand-50` | `#EAF6EF` | 激活底、图标圆底 |
| `--brand-25` | `#F6FAF7` | hover 底、斑马 |

### 2.2 丰收金

| Token | Hex | 用途 |
|---|---|---|
| `--gold-500` | `#F59E0B` | 图表折线/柱状浅端、订单数 |
| `--gold-600` | `#D97706` | **金额色**：价格、销售总额、评分、次卡 accent |
| `--gold-50` | `#FFF4E0` | 金色图标圆底 |

### 2.3 中性

| Token | Hex | 用途 |
|---|---|---|
| `--ink-900` | `#10231A` | 主文字 |
| `--ink-600` | `#5B6B63` | 次文字 |
| `--ink-400` | `#8A9A91` | 辅助文字、轴标签 |
| `--ink-300` | `#A8B4AC` | 占位符 |
| `--bg-page` | `#F6F8F7` | 页面底 |
| `--line` | `#E7ECE9` | 卡片/边框 |
| `--line-soft` | `#F0F3F1` | 内部分隔 |

### 2.4 语义

成功 `#16A34A` / 警告 `#D97706` / 危险 `#DC2626`（实心底文字用白字）；浅底语义：成功 `#E8F8EF`、警告 `#FFF4E0`、危险 `#FEE9E9`。

---

## 3. 字体与间距

- 字体栈：系统 UI（`-apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif`）；数字一律 `font-variant-numeric: tabular-nums`。
- 字号阶梯：页面标题 22px/700 · 卡片标题 15px/700 · 主卡数值 31px/700 · 次卡数值 23px/700 · 表格 13.5px · 辅助 12px · 占位 12.5px。
- 间距按 4px 网格（4/8/12/16/20/24）；卡片内边距 16–20px；页面内容区 padding 24px。
- 圆角：卡片 14–16px、按钮/输入 10px、tag 999px、分页 30px。
- 阴影：仅卡片级微阴影 `0 1px 3px rgba(16,24,40,.04)`；hover `0 8px 24px rgba(16,24,40,.08)`；主按钮品牌投影。

---

## 4. 组件规范

### 4.1 侧栏（LayoutView.vue）

- 白底 236px（折叠 64px），右分隔 `#E7ECE9`；Logo 区：品牌绿稻穗 SVG + 「农臻 Agsell 运营管理后台」。
- 导航按 **经营 / 商品 / 用户 / 内容 / 系统** 五组（`el-menu-item-group`，按业务对象划分），组标题 11px/600 `#A8B4AC`。
  - 经营：数据概览、订单管理、售后管理（交易结果与履约）
  - 商品：商品管理、分类管理、秒杀管理、产地溯源（卖什么 + 怎么卖）
  - 用户：用户管理、评价管理（买的人 + 反馈）
  - 内容：轮播图管理、搜索热词（纯前台展示配置）
  - 系统：系统管理（后台自身）
- 菜单项 13.5px `#5B6B63`、圆角 9px、hover `#F2F5F3`；激活：`#EAF6EF` 底 + `#15803D` 文字 + 左 3px `#16A34A` 指示条。
- 底部用户卡：头像 34px 品牌绿渐变圆 + 姓名/角色 + 退出。
- 折叠态手动隐藏文字 span（EP 折叠不自动隐藏）。

### 4.2 顶栏

- 白底 64px；左：折叠按钮 + 面包屑（当前页 600）；右：全局搜索框（视觉，⌘K 徽标）+ 通知铃铛红点 + 退出按钮。

### 4.3 指标带（KpiPanel.vue）

- **主卡制**：左侧 300px 渐变主卡（`#0E3B25→#15803D→#22A55A`，白色 31px 数值 + 实时 pill + 趋势行 + spark 折线），右侧独立次卡（白底、圆角 14、图标圆底 32px、23px 数值、金色 accent 用于金额）。
- 兼容模式：大面板分块（`title` + `items`），供非主卡场景复用。

### 4.4 表格（admin-table.css）

- 表头 `#F7F9F8` / 12px / `#5B6B63`；行 13.5px、hover `#F6FAF7`、斑马 `#FAFBFA`；状态 tag 圆角 999px（成功浅底绿字/警告浅底金字/危险浅底红字）；价格金色 `#D97706` + 原价划线灰。

### 4.5 按钮 / 分页 / 输入 / 滚动条

- 主按钮：渐变 `#15803D→#22A55A` + 品牌投影，hover 加深抬升；分页 30px 胶囊；输入 focus 绿 ring；滚动条 6px 圆角墨绿半透明。

---

## 5. 图表规范（dashboard-options.ts）

- 轴文字 `#8A9A91`、轴线 `#E7ECE9`、网格 `#EFF3F0`；主系列品牌绿（柱状渐变 `#34C77B→#15803D`、圆角 6）；订单数金线 `#F59E0B` + 金色面积渐变；空态 `#E5E7EB`。
- tooltip：墨绿黑 `rgba(16,35,26,.92)` 白字圆角。
- 环形图：图上不标标签，中心汇总数字 22px/700 `#10231A` + 副标 12px `#8A9A91`，底部图例带百分比。

---

## 6. 登录页（LoginView.vue）

- 左侧品牌区（≥1024px）：晨雾青绿渐变底 + 稻穗 Logo + 标语「让每一份好物都能被高效运营」+ 能力标签 + 经营概览插画卡（果篮/麦穗 SVG + 今日销售额金字）。
- 右侧：白色表单卡「ADMIN CONSOLE / 欢迎回来」，输入 focus 绿 ring，登录按钮渐变 + 品牌投影 + hover 抬升。
- `<1024px` 隐藏品牌区，表单居中。

---

## 7. 目录结构（v4 整理后）

```
src/
├─ views/admin/                 # 框架与概览
│  ├─ LoginView.vue  LayoutView.vue  DashboardView.vue  dashboard-options.ts
│  ├─ list/                      # 运营列表页 ×11（User/Category/Product/Order/Review/Banner/AfterSales/Traceability/Seckill/HotWord/Admin）
│  └─ system/                    # 系统管理页 ×2（SystemConfig/OperationLog）
├─ components/admin/             # PageHeader.vue · KpiPanel.vue
├─ styles/                       # theme.css（token 层）· admin-table.css（表格层）
```

---

## 8. v3 → v4 迁移要点

| 项 | v3 | v4 |
|---|---|---|
| 页面底/卡片 | 中性灰白 `#F4F6F5` | 暖灰 `#F6F8F7`，卡片圆角 14px |
| 侧栏 | 深翡翠渐变 | 浅色白底 + 分组导航 + 激活指示条 |
| 指标带 | 大面板分块 | 渐变主卡 + 独立次卡 |
| 图表主系列 | `#10B981` | `#15803D`（渐变 `#34C77B→#15803D`），订单线金 `#F59E0B` |
| 金额 | 品牌绿 | 丰收金 `#D97706` |
| tag | 直角 | 圆角 999px |
| 分页 | 直角 | 30px 胶囊 |
| 登录页 | 单一表单 | 双栏品牌区 + 表单 |

**落地文件清单**（本次改造）：`src/styles/theme.css`、`src/styles/admin-table.css`、`src/App.vue`、`src/components/admin/PageHeader.vue`、`src/components/admin/KpiPanel.vue`、`src/views/admin/LayoutView.vue`、`src/views/admin/LoginView.vue`、`src/views/admin/DashboardView.vue`、`src/views/admin/dashboard-options.ts`、列表页色值批量对齐、`src/api/admin.ts`（仅补 `pageNum` 类型声明）、`src/router/guards.ts`（仅视图 import 路径随目录调整）。
