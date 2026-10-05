# agsell 管理端设计系统 v3.0（企业级高级感）

> ⚠️ **已由 v4.0 取代**：本文档为历史版本（2026-09-09 生效）。管理端样式现以 `doc/design-system-v4.md`（青翠绿 × 丰收金 × 中性底）为**唯一权威来源**；v4 落地后本文档所述色值/字号/组件规范不再适用，页面如有冲突以 v4 为准。保留本文档仅供追溯。

> 生成方式：ui-ux-pro-max 设计智能（`--design-system` Trust & Authority / 企业级后台模式）· design-taste-frontend 设计判定（Enterprise Operations Console）· impeccable 质量门（引擎离线，改由项目上下文直接核定）
> 适用范围：**管理端** `agsell-frontend`（Vue 3 + Element Plus 2.x + ECharts）。小程序端 `agsell-uniapp` 不在此次改造范围。
> 生效日期：2026-09-09 · 本文档为管理端页面样式**唯一权威来源**，页面改造必须逐条落实本章规则，不得引入文档之外的裸色值/裸字号/裸间距。
> v3.0 相对 v2.0（MASTER.md 有机生态版）的核心变更：**从「有机浅绿可爱风」升级为「深翡翠品牌 + 中性高级底」的企业级视觉**——页面底色中性化、卡片去绿描边改分层阴影、侧栏深翡翠渐变、标题层级加大、表格/弹窗/分页精修、共享组件落地。

---

## 0. 设计判定（Design Read）

> Reading this as: **enterprise operations console（B2B 管理工具）** for ops staff & admins, with a **premium enterprise language** — deep emerald brand + neutral slate surfaces, crisp data density, quiet elevation, restrained motion — leaning toward a **custom Element Plus token layer** with Linear-style precision.

三旋钮（design-taste-frontend 约定）：`DESIGN_VARIANCE: 5`（企业级=克制与秩序）· `MOTION_INTENSITY: 4`（150–250ms 轻动效）· `VISUAL_DENSITY: 6`（数据密集但清晰）。

---

## 1. 设计原则（六条企业级铁律）

1. **中性为底，品牌点睛**：页面/卡片大面积中性灰白，品牌绿只用于关键时刻——主按钮、激活态、图表系列、重点数据、侧栏。禁止每张卡片都描绿边。
2. **信息密度与清晰度平衡**：表格行高 48px、正文 14px、表格 13px；数字一律 `tabular-nums` 对齐。
3. **层级清晰的排版**：页面标题 20px/600 → 卡片标题 15px/600 → 正文 14px/400 → 辅助 12px，间距按 4px 网格。
4. **克制动效**：hover/聚焦/展开 150–250ms ease-out；无冗余弹跳与无限动画；`prefers-reduced-motion` 全量降级。
5. **可访问性优先**：正文对比度 ≥ 4.5:1（对照 §2.4 已测色表）、可见 focus ring、键盘可达、状态标签「颜色 + 文字」双通道。
6. **组件复用**：页面标题统一 `PageHeader`、统计卡统一 `StatCard`、Element Plus 组件样式由全局 token 层统一覆盖，禁止页面内散写裸样式。

---

## 2. 色彩系统

### 2.1 品牌绿阶（保留原品牌色相，加深层次）

| Token | Hex | 用途 |
|---|---|---|
| `--brand-900` | `#0A2A1B` | 侧栏渐变深端、页面标题（深翡翠） |
| `--brand-800` | `#0E3B25` | 侧栏渐变浅端、卡片标题 |
| `--brand-700` | `#14532D` | 主色 hover/active、深绿文字 |
| `--brand-600` | `#15803D` | **品牌主色**：主按钮、链接、激活态、开关 |
| `--brand-500` | `#166534` | 主按钮 hover |
| `--brand-400` | `#16A34A` | 成功实心 |
| `--brand-300` | `#22C55E` | 指示条、focus ring、图表亮部 |
| `--brand-200` | `#BBF7D0` | 浅绿描边（仅装饰） |
| `--brand-100` | `#DCFCE7` | success 类标签底 |
| `--brand-50` | `#F0FDF4` | 极浅绿底（选中行、图标底） |

### 2.2 中性层（v3 新增，替代「处处浅绿底」）

| Token | Hex | 用途 |
|---|---|---|
| `--neutral-bg` | `#F4F6F5` | **页面背景**（微绿中性灰，让白卡与内容更突出） |
| `--neutral-card` | `#FFFFFF` | 卡片/表格/弹窗背景 |
| `--neutral-muted` | `#F3F5F4` | 表头底、悬浮底、禁用底 |
| `--neutral-border` | `#E3E7E5` | 卡片描边（中性，装饰性） |
| `--neutral-border-strong` | `#D1D5DB` | 输入框/可交互控件边框 |
| `--neutral-zebra` | `#FAFBFA` | 表格斑马纹 |

### 2.3 文字色

| Token | Hex | 用途 |
|---|---|---|
| `--text-heading` | `#0A2A1B` | 页面标题（深翡翠） |
| `--text-card-title` | `#0E3B25` | 卡片标题 |
| `--text-regular` | `#1F2937` | 正文/表格体 |
| `--text-secondary` | `#6B7280` | 次要文字、表头、面包屑（白底 4.83:1 ✅） |
| `--text-placeholder` | `#9CA3AF` | 仅占位符/禁用（禁止正文） |
| `--text-inverse` | `#FFFFFF` | 品牌色底文字 |

### 2.4 语义与状态色（浅底深字，沿用 v2.0 已实测组合）

| 语义 | 标签底 | 标签文字 | 实心 | 用途 |
|---|---|---|---|---|
| success | `#DCFCE7` | `#166534` | `#16A34A` | 上架、已完成、正常、已回复、启用 |
| warning | `#FEF3C7` | `#92400E` | `#D97706` | 待付款、库存预警、待审核 |
| danger | `#FEE2E2` | `#991B1B` | `#DC2626` | 禁用、删除、售后中、取消 |
| primary | `#BBF7D0` | `#14532D` | `#15803D` | 待发货、进行中 |
| info | `#F1F2F4` | `#4B5563` | `#6B7280` | 已下架、中性、未回复 |
| accent（丰收金） | `#FFF7ED` | `#9A3412` | `#A16207` | **价格、销售额、评分**（白底 4.92:1 ✅） |

> 禁止在 muted 底上用 `#6B7280`（4.18:1 不达标），用 `#475569`；禁止 `#9CA3AF` 作正文。完整对比度实测表沿用 MASTER.md §2.6。

### 2.5 深色侧栏（管理端导航）

| Token | Hex | 用途 |
|---|---|---|
| 侧栏背景 | `linear-gradient(180deg, #0E3B25 0%, #0A2A1B 100%)` | 深翡翠渐变，企业感 |
| 菜单文字 | `rgba(255,255,255,0.72)` | 默认态 |
| 菜单 hover | `rgba(255,255,255,0.08)` | — |
| 菜单激活 | 底 `rgba(255,255,255,0.12)` + 文字 `#FFFFFF` + 左侧 3px `#22C55E` 指示条 | 圆角 8px 内凹激活块 |

---

## 3. 字体系统

### 3.1 字体栈（本地优先，不引入外部 CDN，避免离线降级）

```css
font-family: 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', 'Noto Sans SC',
  -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', 'Plus Jakarta Sans', sans-serif;
```

- 西文/数字优先落到本机已有字体；中文回落系统栈。如需启用 Inter/Plus Jakarta Sans，自行下载后放 `src/assets/fonts` 本地引入。
- 数字金额统一 `font-variant-numeric: tabular-nums`。

### 3.2 字号阶梯（管理端）

| 层级 | Token | 字号/字重 | 用途 |
|---|---|---|---|
| 大标题 | `--fs-display` | 24px / 700 | Dashboard 页标题 |
| 页面标题 | `--fs-page` | 20px / 600 | 各页 PageHeader 标题 |
| 卡片标题 | `--fs-card-title` | 15px / 600 | 卡片/区块标题 |
| 正文 | `--fs-body` | 14px / 400 | 默认正文 |
| 表格/标签 | `--fs-table` | 13px / 500 | 表格体、按钮、tag |
| 辅助 | `--fs-caption` | 12px / 400 | 时间、角标、描述 |

行高：正文 1.5、标题 1.4；页面标题可加 `letter-spacing: 0.01em`。

---

## 4. 间距系统（4px 网格）

| Token | 值 | 用途 |
|---|---|---|
| space-1 | 4px | 图标与文字间 |
| space-2 | 8px | 相邻元素最小间距 |
| space-3 | 12px | 小控件内边距 |
| space-4 | 16px | 卡片内边距、表单项间距 |
| space-5 | 20px | 卡片标题与内容间距 |
| space-6 | 24px | **页面容器内边距、区块间距** |
| space-8 | 32px | 页面级留白 |

---

## 5. 圆角与阴影（分层质感，替代「绿描边识别」）

| Token | 值 | 用途 |
|---|---|---|
| radius-sm | 6px | 标签、输入框 |
| radius-base | 8px | 按钮、菜单项 |
| radius-lg | 12px | 卡片、弹窗、表格容器 |
| radius-xl | 16px | 登录卡片、大面板 |

| 阴影 | 值 | 用途 |
|---|---|---|
| shadow-sm | `0 1px 2px rgba(16,24,40,0.05)` | 卡片默认 |
| shadow-base | `0 1px 3px rgba(16,24,40,0.06), 0 4px 12px rgba(16,24,40,0.05)` | 搜索卡/常规面板 |
| shadow-lg | `0 12px 32px rgba(16,24,40,0.12)` | 弹窗、悬浮卡、hover 提升 |

卡片边界识别：**1px 中性描边 + 阴影双层**（1.21:1 的绿描边不再单独承担边界职责）。

---

## 6. 组件规范

### 6.1 卡片（el-card）
- 白底、1px `#E3E7E5` 描边、圆角 12px、`shadow-sm`；hover 提升 `shadow-lg` + 描边 `#D8DEDB`。
- 标题 15px/600 `#0E3B25`；内边距 16–20px。
- 搜索卡与表格卡保持上下间距 16px。

### 6.2 按钮
- 默认高 32px、圆角 8px；主按钮 `#15803D` 白字，hover `#166534`，active `#14532D`。
- 次要按钮：白底 1px `#D1D5DB` 边框；文字按钮（link）用于表格操作列。
- focus：`outline: 2px solid #22C55E; outline-offset: 2px`。

### 6.3 输入框 / 选择器
- 边框 `#D1D5DB`，圆角 8px；focus 边框 `#15803D` + `0 0 0 3px rgba(21,128,61,0.15)`。
- 占位符 `#9CA3AF`；表单必须有可见 label。

### 6.4 表格
- 表头：底 `#F3F5F4`、字 `#475569` 13px/600、padding `12px 16px`；行高 48px。
- 单元格 padding `12px 16px`、14px `#1F2937`；斑马 `#FAFBFA`；hover `#F4F9F5`。
- 操作列 `fixed="right"`，白底渐变遮罩保证可读性；窄屏 `overflow-x: auto`。

### 6.5 状态标签
- 浅底深字（色值 §2.4），圆角 6px、高 22px、字号 12px/500、padding 0 8px。

### 6.6 弹窗（el-dialog）
- 圆角 12px；header padding `20px 24px`，标题 16px/600 `#0A2A1B`；body padding `20px 24px`；footer 右对齐。
- 遮罩 `rgba(15,23,42,0.5)`。

### 6.7 分页
- 简洁样式：按钮 28px 方形圆角 6px；当前页品牌绿白字；禁用灰。

### 6.8 导航（侧栏 + 顶栏）
- 侧栏 240px（v3 加宽），深翡翠渐变；折叠态 64px 图标模式；`<768px` 自动折叠，顶栏提供手动折叠按钮。
- 顶栏 64px 白底、底边框 `#E5E8E6`；左侧面包屑「首页 / 页面名」；右侧管理员信息（头像圆标 + 姓名）+ 退出。

### 6.9 指标面板（KpiPanel，共享组件）

- 每行指标由**一个大面板包裹**（参考主流运营大盘排版）：面板白底、1px `#E3E7E5` 描边、圆角 12px、`shadow-sm`；标题行（品牌小竖条 + 标题 15px/600 + 右侧口径说明）。
- 面板内指标块**无独立边框**：≥1200px 一行铺满、块间 1px `#EEF1EF` 竖分隔线；以下自适应换行（`auto-fit minmax(160px,1fr)`）。
- 指标块结构：标签 13px `#6B7280` + 数值 24px/700 `#1F2937`（`tabular-nums`）；金额类（销售总额）数值用丰收金 `#A16207`（`accent`）。

---

## 7. 布局与响应式

- 侧栏 240px → 折叠 64px；顶栏 64px；内容区 `#F4F6F5` padding 24px（大屏 32px）。
- 断点：`<768px` 侧栏自动折叠（图标模式）；`768–1200px` 标准栅格；`>1200px` 宽屏拉伸。
- Dashboard 栅格：指标区每行一个 `KpiPanel` 大面板（用户 4 项 / 商品与订单 6 项，≥1200px 一行铺满）；趋势图 `lg:12`；构成图 `md:12`。
- 表格固定操作列 + 横向滚动；图表容器固定高度 240px，ResizeObserver 自动重绘（BaseChart 已支持）。
- 触控目标 ≥ 24px（图标按钮），相邻目标间距 ≥ 8px。

---

## 8. 图表规范（ECharts，Dashboard）

1. **系列色板（固定顺序）**：`#15803D, #10B981, #D97706, #2563EB, #64748B, #DC2626`（品牌绿起头，丰收金用于销售额，蓝仅扩展）。
2. 柱状：品牌绿，可加 `LinearGradient(0,0,0,1, #22C55E → #15803D)` 渐变 + `borderRadius: [6,6,0,0]`；空态灰 `#E5E7EB`。
3. 折线：2px 平滑 `#15803D`，面积渐变 `rgba(34,197,94,0.15)`；虚线分轴。
4. 环形图（清爽排版）：圆环 `radius: ['46%','70%']`、`borderRadius: 4`；**图上不显示常驻标签**，改为「中心汇总数字（22px/700）+ 副标题」+ 底部图例带百分比（`formatter: 名称  占比`），hover 才显示明细标签；tooltip 开启。
5. 网格线 `#EEF1EF`；坐标轴文字 `#6B7280` 12px；轴线隐藏。
6. 无障碍：tooltip/图例至少其一存在，颜色不作为唯一信息通道。

---

## 9. 共享组件清单（组件复用落地）

| 组件 | 路径 | 说明 |
|---|---|---|
| `PageHeader` | `src/components/admin/PageHeader.vue` | 页面标题 + 描述 + 右侧操作区 slot，全部页面复用 |
| `KpiPanel` | `src/components/admin/KpiPanel.vue` | Dashboard 指标面板：一行一个大面板包裹，内部指标块竖分隔（label/value/accent） |
| 全局 token 层 | `src/styles/theme.css` | Element Plus 组件级全覆盖（唯一样式权威） |
| 全局表格/分页/卡片 | `src/styles/admin-table.css` | 表格、分页、卡片头、状态标签 |

> 业务逻辑零改动：本次只改布局、配色、样式、排版、组件；`<script>` 中的接口调用、状态管理、事件处理全部保留。

---

## 10. 可访问性与动效

- 对比度：正文/次要文字 ≥ 4.5:1（对照 §2.4 与 MASTER §2.6，禁止引入新色）。
- focus ring：主色 2px `#22C55E` + offset 2px，键盘可达。
- 状态标签「颜色 + 文字」双通道；图标按钮带 `aria-label`/文本；装饰图标 `aria-hidden`。
- 动效：hover/focus 150ms、显隐 200ms、弹窗 250ms，全部 `ease-out`；`prefers-reduced-motion` 时降级为 0ms。
- 滚动条：细滚动条（8px、圆角、`#E3E7E5` 轨道），提升桌面观感。

---

## 11. 页面级改造清单

| 页面 | 改造要点 |
|---|---|
| LayoutView | 深翡翠渐变侧栏 240px、激活块圆角 + 指示条、顶栏 64px + 面包屑 + 管理员头像、手动折叠按钮、`<768px` 自动折叠、内容区 `#F4F6F5` 24px |
| LoginView | 左右分栏：左侧品牌区（深绿渐变 + 稻穗 Logo + 标语），右侧表单卡圆角 16px；`<768px` 隐藏品牌区 |
| DashboardView | `KpiPanel` 指标面板（每行一个大面板包裹，块间竖分隔）、PageHeader、图表按 §8 新配色渐变、销售卡丰收金 |
| ProductListView | PageHeader + 搜索卡/表格卡精修、价格丰收金、状态 tag §2.4、弹窗规范 |
| OrderListView | 同上；状态映射沿用现有逻辑；详情/发货弹窗精修 |
| UserListView | 头像圆标、状态 tag、开关品牌绿 |
| CategoryListView | 同上；图标预览精修 |
| ReviewListView | 评分丰收金、回复状态 tag、弹窗精修 |
| AfterSalesListView | 退款金额丰收金、状态 tag、详情弹窗分区精修 |
| BannerListView | 预览图卡片化、状态开关、弹窗精修 |

---

## 12. 验收清单（改造后逐项核对）

- [ ] `vue-tsc` 无新错误；dev server 热更新正常（**不重启进程**）；
- [ ] 页面背景 `#F4F6F5`、卡片中性描边 + 阴影、无残留 `#BBF7D0` 卡片描边；
- [ ] 全部页面标题为 `PageHeader` 组件；Dashboard 指标为 `KpiPanel` 大面板（每行一个）；
- [ ] 表格/分页/弹窗/标签统一（§6）；图表新色板（§8）；
- [ ] 对比度达标、focus ring 存在、`prefers-reduced-motion` 降级；
- [ ] 业务逻辑零改动（git diff 仅涉及模板/样式/共享组件）；
- [ ] 1440px / 768px / 375px 截图核验，无横向溢出、无样式破版。

---

## 13. 相关文档

- 上一版本：`doc/MASTER.md`（v2.0 有机生态版，小程序端仍以其为准）
- 原型说明：`doc/admin-prototype-design.md`
- 需求依据：`doc/需求文档.md` 第 5 章（管理后台 Web）
