# agsell 设计系统 MASTER（v2.0 · 有机生态升级版）

> 生成方式：ui-ux-pro-max 设计智能
> - `--design-system`：Pattern = **Feature-Rich Showcase** / Style = **Organic Biophilic**（自然、有机、圆润、可持续）
> - 专项查询：`--domain ux`（响应式布局 / 卡片 / 表格溢出）· `--domain typography`（电商字体层级）· `--domain chart`（仪表盘图表规范）· `--stack vue`（Vue 3 SFC / 懒加载 / Pinia）
> 适用范围：**双端统一** —— 管理端 `agsell-frontend`（Vue 3 + Element Plus 2.x + ECharts）与小程序端 `agsell-uniapp`（uni-app，H5 + 微信小程序）
> 生效日期：2026-09-08 · 本文档为两端页面的**唯一样式权威来源**，页面改造必须逐条落实本章规则，不得引入本文档之外的裸色值/裸字号/裸间距。
> v2.0 相对 v1.0 的变更：① 页面背景升级为生态浅绿 `#F0FDF4`，新增 muted 中性层 `#E8F0F1`（表头/悬浮底）；② 图表色板全面替换为品牌序列（告别 Element 默认蓝）；③ 卡片圆角升级（管理端 12px、小程序 24rpx），加入品牌绿描边层级；④ 引入西文可选增强字体 Lora/Raleway（中文仍走系统字体栈）；⑤ 增加图标字体/表格可访问性验收项。

---

## 1. 产品定位与设计原则

**产品**：农产品（生鲜）电商平台。小程序面向 C 端消费者（逛、选、买、评）；管理端面向运营人员（商品、订单、用户、评价、轮播图运营）。

**风格关键词**：自然、新鲜、可信赖、有机、圆润、扁平（Organic Biophilic）。视觉基调 = 品牌绿 + 丰收金 + 生态浅绿底，卡片为白底 + 细绿描边 + 自然阴影。

**六条铁律**（页面改造验收项）：

1. **双端一品牌**：两端共用同一套色彩、字号、间距、圆角、状态规范；当前管理端蓝 `#1a7bf5`、小程序绿 `#4CAF50`、theme.json 橙 `#ff6b00` 三色混用的局面必须统一为品牌绿 `#15803D`。
2. **禁止 emoji 当图标**：现有 `🌾 🔍 🔥 🆕 🍊 🥬 🌿` 等全部替换为矢量图标（管理端用 `@element-plus/icons-vue` + 稻穗 SVG Logo，小程序用内联 SVG / 本地图标）。emoji 仅可作为文案语气词，不得承担导航、按钮、状态等结构功能。
3. **对比度硬约束**：正文与背景 ≥ 4.5:1；大号/加粗文字 ≥ 3:1；非文字控件边界 ≥ 3:1。所有主色对已在本文档 §2.6 实测，改造时不得自行换色。
4. **不以颜色为唯一信息载体**：状态标签除颜色外必须带文字；错误提示 = 图标 + 文案；图表除颜色外必须有图例/标签/百分比文字。
5. **状态即时反馈**：所有请求有 loading；提交后有成功/失败提示；触控/点击有按压反馈（80–150ms 内）。
6. **有机圆润质感**：卡片统一圆角（管理端 12px / 小程序 24rpx）、细绿描边 `#BBF7D0`、自然阴影；禁止生硬直角、禁止 emoji 式图标、禁止无意义的粗黑描边。

---

## 2. 色彩系统

### 2.1 设计系统 Token（ui-ux-pro-max 输出，双端共用）

| Role | Hex | CSS Variable | 用途 |
|---|---|---|---|
| Primary | `#15803D` | `--color-primary` | 品牌主色：主按钮、链接、激活态、导航栏 |
| On Primary | `#FFFFFF` | `--color-on-primary` | 主色底上的文字 |
| Secondary | `#22C55E` | `--color-secondary` | 强调、图标、渐变亮部、指示条 |
| On Secondary | `#0F172A` | `--color-on-secondary` | 深色底上的亮绿文字（仅大号/粗体） |
| Accent/CTA | `#A16207` | `--color-accent` | 丰收金：价格、销售额、促销、加购 CTA、评分 |
| On Accent/CTA | `#FFFFFF` | `--color-on-accent` | 丰收金底上的文字 |
| Background | `#F0FDF4` | `--color-background` | 页面背景（生态浅绿，两端一致） |
| Foreground | `#14532D` | `--color-foreground` | 页面标题、卡片标题（深绿） |
| Card | `#FFFFFF` | `--color-card` | 卡片/表格/弹窗背景 |
| Card Foreground | `#14532D` | `--color-card-foreground` | 卡片标题文字 |
| Muted | `#E8F0F1` | `--color-muted` | 表头底、悬浮底、禁用底（灰绿中性层） |
| Muted Foreground | `#475569` | `--color-muted-foreground` | muted 底上的文字（#6B7280 在 #E8F0F1 上仅 4.18:1 不达标，必须用 #475569） |
| Border | `#BBF7D0` | `--color-border` | 卡片描边、品牌分隔线 |
| Destructive | `#DC2626` | `--color-destructive` | 危险动作、删除、错误 |
| On Destructive | `#FFFFFF` | `--color-on-destructive` | 危险实心按钮文字 |
| Ring | `#15803D` | `--color-ring` | 焦点环、选中描边 |

### 2.2 品牌绿阶（两端共用）

| Token | Hex | 用途 |
|---|---|---|
| `--color-primary-50` | `#F0FDF4` | 极浅绿底（选中行、图标底、页面背景） |
| `--color-primary-100` | `#DCFCE7` | 浅绿底（success 类标签底） |
| `--color-primary-200` | `#BBF7D0` | 标签底、卡片描边、禁用态填充 |
| `--color-primary-500` | `#22C55E` | 强调、图标、渐变亮部、指示条 |
| `--color-primary-600` | `#16A34A` | 次级主色（成功实心） |
| `--color-primary-700` | `#15803D` | **品牌主色**：主按钮、链接、激活态、导航栏、tab 选中 |
| `--color-primary-800` | `#166534` | 主按钮 hover |
| `--color-primary-900` | `#14532D` | 深绿：侧栏背景、标题文字、主按钮 active |

### 2.3 语义与状态色（浅底深字方案，全部实测达标）

| 语义 | 标签底 | 标签文字 | 实心（用于按钮/开关） | 用途 |
|---|---|---|---|---|
| success | `#DCFCE7` | `#166534` | `#16A34A` | 上架、已完成、正常、已回复、启用 |
| warning | `#FEF3C7` | `#92400E` | `#D97706` | 待付款、库存预警、待审核 |
| danger | `#FEE2E2` | `#991B1B` | `#DC2626` | 禁用、删除、售后中、取消（危险动作） |
| primary | `#BBF7D0` | `#14532D` | `#15803D` | 待发货、进行中（品牌强调态） |
| info（灰） | `#F3F4F6` | `#4B5563` | `#6B7280` | 已下架、已完成（中性）、未回复 |
| accent（丰收金） | `#FFF7ED` | `#9A3412` | `#A16207` | **价格、销售额、促销、加购 CTA、评分** |

### 2.4 中性色

| Token | Hex | 用途 |
|---|---|---|
| `--text-heading` | `#14532D` | 页面标题、卡片标题（深绿） |
| `--text-regular` | `#1F2937` | 正文 |
| `--text-secondary` | `#6B7280` | 次要文字、表头、面包屑（页面底 #F0FDF4 上 4.62:1 ✅） |
| `--text-muted-on-muted` | `#475569` | muted 底 #E8F0F1 上的辅助文字（6.55:1 ✅） |
| `--text-placeholder` | `#9CA3AF` | **仅限**输入占位符、禁用辅助文字（2.43:1，禁止用于正文） |
| `--text-inverse` | `#FFFFFF` | 品牌色底上的文字 |
| `--bg-page` | `#F0FDF4` | 页面背景（两端一致，生态浅绿） |
| `--bg-card` | `#FFFFFF` | 卡片/表格背景 |
| `--bg-muted` | `#E8F0F1` | 表头底、悬浮底、禁用底（灰绿中性层） |
| `--bg-mask` | `rgba(15,23,42,0.5)` | 弹窗遮罩 |
| `--border` | `#BBF7D0` | 卡片描边、品牌分隔线（装饰性，边界对比由阴影辅助） |
| `--border-strong` | `#D1D5DB` | 输入框边框、可交互控件边框（1.47:1，须配 focus ring） |

### 2.5 深色模式（小程序 theme.json，管理端预留）

| Token | Light | Dark |
|---|---|---|
| bg-page | `#F0FDF4` | `#181818` |
| bg-card | `#FFFFFF` | `#1F2937` |
| text-regular | `#1F2937` | `#F3F4F6` |
| text-secondary | `#6B7280` | `#9CA3AF` |
| border | `#BBF7D0` | `#2F4A3A` |
| primary（导航栏/tab 选中） | `#15803D` | `#22C55E`（深底上用亮绿） |

### 2.6 对比度实测表（WCAG 2.1 AA，已用相对亮度公式核算）

| 色对 | 对比度 | 用途 | 判定 |
|---|---|---|---|
| `#15803D` / `#FFFFFF` | 5.02:1 | 主按钮文字、品牌底白字 | ✅ |
| `#15803D` / `#F0FDF4` | 4.79:1 | 主色链接于页面底 | ✅ |
| `#14532D` / `#FFFFFF` | 9.11:1 | 标题/深绿底白字 | ✅ |
| `#1F2937` / `#F0FDF4` | 14.02:1 | 正文于页面底 | ✅ |
| `#6B7280` / `#F0FDF4` | 4.62:1 | 次要文字于页面底 | ✅ |
| `#6B7280` / `#FFFFFF` | 4.83:1 | 次要文字于卡片底 | ✅ |
| `#475569` / `#E8F0F1` | 6.55:1 | muted 底辅助文字 | ✅ |
| `#475569` / `#FFFFFF` | 7.58:1 | 辅助说明于白底 | ✅ |
| `#A16207` / `#FFFFFF` | 4.92:1 | 价格文字（白底） | ✅ |
| `#DC2626` / `#FFFFFF` | 4.83:1 | 危险实心按钮 | ✅ |
| `#166534` / `#DCFCE7` | 6.49:1 | success 标签 | ✅ |
| `#14532D` / `#BBF7D0` | 7.52:1 | primary 标签 / 卡片描边上的标题 | ✅ |
| `#92400E` / `#FEF3C7` | 6.37:1 | warning 标签 | ✅ |
| `#991B1B` / `#FEE2E2` | 6.80:1 | danger 标签 | ✅ |
| `#4B5563` / `#F3F4F6` | 6.87:1 | info 标签 | ✅ |
| `#9A3412` / `#FFF7ED` | 6.88:1 | accent 标签 | ✅ |
| `#6B7280` / `#E8F0F1` | 4.18:1 | **禁止** muted 底上用 #6B7280，改用 #475569 | ⚠️ 受限 |
| `#9CA3AF` / `#F0FDF4` | 2.43:1 | **禁止**用于正文/按钮文字，仅占位符 | ⚠️ 受限 |
| `#22C55E` / `#FFFFFF` | 2.28:1 | **禁止**亮绿白字小号；仅大号粗体/非文字控件 | ⚠️ 受限 |
| `#BBF7D0` / `#FFFFFF` | 1.21:1 | 卡片描边为装饰性，边界识别须靠阴影辅助 | ⚠️ 装饰 |

> 结论：实心状态按钮一律用品牌绿 `#15803D`（5.02:1）；success/warning 不另做实心白字小号按钮。小号状态一律用**浅底深字标签**。卡片边界不能只依赖 1.21:1 的描边，须同时保留 `shadow-sm`。

---

## 3. 字体系统

### 3.1 字体栈（中文优先，西文可选增强）

```css
/* 主栈：中文系统字体（两端一致） */
font-family: 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei',
  'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;

/* 可选增强（西文/数字）：引入后仅影响拉丁字符，中文仍回落系统字体 */
--font-heading: 'Lora', 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', serif;
--font-body: 'Raleway', 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', sans-serif;
```

- 管理端如需启用 Lora/Raleway，在 `index.html` 引入 Google Fonts 链接并保持 font-display: swap；小程序端不引入（微信环境无 Google Fonts），中文栈即为主栈。
- 数字金额统一 `font-variant-numeric: tabular-nums`（价格列对齐）。

### 3.2 字号阶梯（管理端 px / 小程序 rpx）

| 层级 | Token | 管理端 | 小程序 | 字重 | 用途 |
|---|---|---|---|---|---|
| 辅助 | `--fs-caption` | 12px | 22rpx | 400 | 标签、时间、角标 |
| 表格/标签 | `--fs-table` | 13px | 24rpx | 500 | 表格体、tag |
| 正文 | `--fs-body` | 14px | 28rpx | 400 | 默认正文 |
| 卡片标题 | `--fs-card-title` | 15px | 30rpx | 600 | 卡片/区块标题 |
| 页面标题 | `--fs-page-title` | 16px | 32rpx | 600 | 页面主标题 |
| 强调数字 | `--fs-stat` | 20px | 36rpx | 600 | 统计数值、价格 |
| 大标题 | `--fs-display` | 24px | 40rpx | 600 | 登录页主标题、banner 文案 |

行高：正文 1.5，标题 1.4；字母数字用 tabular-nums；标题可用 `--font-heading`（Lora）增强品牌气质。

---

## 4. 间距系统（4px 网格）

| Token | 管理端 | 小程序 | 用途 |
|---|---|---|---|
| space-1 | 4px | 8rpx | 图标与文字间 |
| space-2 | 8px | 16rpx | 相邻元素最小间距 |
| space-3 | 12px | 24rpx | 卡片内边距（小程序 24rpx） |
| space-4 | 16px | 32rpx | 管理端卡片内边距、表单项间距 |
| space-5 | 20px | 40rpx | 管理端页面容器内边距、区块间距 |
| space-6 | 24px | 48rpx | 大区块间距、小程序 section 间距 |
| space-8 | 32px | 64rpx | 页面级留白 |

触控目标：小程序**所有可点元素 ≥ 88rpx（44px@375）**，相邻目标间距 ≥ 16rpx；管理端按钮默认高度 32px，icon-only 按钮加 padding 保证 ≥ 24px 命中区。

---

## 5. 圆角与阴影（有机升级）

| Token | 管理端 | 小程序 | 用途 |
|---|---|---|---|
| radius-sm | 6px | 12rpx | 标签、输入框 |
| radius-base | 8px | 16rpx | 按钮、弹窗、小卡片 |
| radius-lg | 12px | 24rpx | **卡片、banner（主卡圆角）** |
| radius-pill | 999px | 999rpx | 胶囊按钮、搜索框、tab |

阴影（扁平化基调，卡片以「白底 + 细描边 + 阴影」三层识别）：

- `shadow-sm`：`0 1px 2px rgba(16,24,40,0.06)`
- `shadow-base`：`0 2px 8px rgba(16,24,40,0.06)`
- `shadow-lg`：`0 8px 24px rgba(16,24,40,0.10)`（弹窗/抽屉）
- 管理端 `el-card` 统一 `shadow="never"` + 1px `#BBF7D0` 描边 + 圆角 12px（卡片边界识别由阴影辅助，见 §2.6）。

---

## 6. 卡片规范

### 6.1 管理端卡片

| 属性 | 值 |
|---|---|
| 背景 | `#FFFFFF` |
| 描边 | 1px `#BBF7D0`（品牌绿细描边） |
| 圆角 | 12px |
| 内边距 | 16px（space-4） |
| 阴影 | `shadow-sm`（hover 提升为 `shadow-base`） |
| 标题 | 15px 600 `#14532D`（`--fs-card-title` / `--color-card-foreground`） |

### 6.2 小程序卡片

| 属性 | 值 |
|---|---|
| 背景 | `#FFFFFF` |
| 描边 | 1rpx `#BBF7D0` |
| 圆角 | 24rpx |
| 边距 | 24rpx（页面四周） |
| 内边距 | 24rpx |
| 阴影 | `shadow-sm` |
| 标题 | 32rpx 600 `#14532D` |

---

## 7. 组件状态规范

### 7.1 主按钮（两端）

| 状态 | 管理端 | 小程序 |
|---|---|---|
| default | 底 `#15803D` 字 `#FFF` 圆角 8px | 高 88rpx 胶囊 44rpx 字 32rpx |
| hover | 底 `#166534`（primary-800） | —（移动端无 hover） |
| active/pressed | 底 `#14532D` | 底 `#14532D` + 透明度 0.9 |
| focus | `outline: 2px solid #22C55E; outline-offset: 2px` | — |
| disabled | 底 `#E5E7EB` 字 `#9CA3AF` 无阴影 | 同左（或 opacity 0.4） |
| loading | 保留原尺寸 + spinner，禁止尺寸跳动 | 同左 |

次要按钮（ghost/plain）：1px `#15803D` 边框 + `#15803D` 文字，hover 浅绿底 `#F0FDF4`。

### 7.2 输入框

- 默认边框 `#D1D5DB`，聚焦边框 `#15803D` + `box-shadow: 0 0 0 3px rgba(21,128,61,0.15)`；
- 错误：边框 `#DC2626` + 错误文案（**图标 + 文字**，颜色非唯一信号）；
- 占位符 `#9CA3AF`；禁用底 `#F3F4F6` 字 `#9CA3AF`；
- 表单必须有可见 label（禁止仅 placeholder 充当 label）。

### 7.3 状态标签（两端统一规范）

- 高度：管理端 22px / 小程序 44rpx；字号 12px/24rpx 字重 500；圆角 6px/12rpx；内边距 0 8px / 0 16rpx；
- 一律**浅底深字**（色值见 §2.3），不透明度禁用 0.4。

### 7.4 表格（管理端）

- 表头：底 `#E8F0F1`（muted）、字 `#475569` 13px 500；行高 44px；
- 行 hover 底 `#F0FDF4`；选中行 `#DCFCE7`；
- 边框 `#E5E7EB`；斑马纹底 `#FAFAFA`（可保留）；
- 窄屏 `overflow-x: auto` 横向滚动，禁止撑破布局。

### 7.5 开关 / 导航

- 开关：开 `#15803D`，关 `#D1D5DB`；
- 侧栏（管理端）：底 `#14532D` 深绿，菜单文字 `rgba(255,255,255,0.78)`，激活项底 `#15803D` 字纯白 + 左侧 3px `#22C55E` 指示条；
- tabBar（小程序）：默认字 `#9CA3AF`，选中 `#15803D`，底 `#FFFFFF`，顶部 1px 边框。

### 7.6 动效

| Token | 时长 | 场景 |
|---|---|---|
| motion-fast | 120ms | 按压、hover |
| motion-base | 200ms | 显隐、开关 |
| motion-slow | 300ms | 弹窗、抽屉 |

所有过渡遵循 `ease-out`；实现 `prefers-reduced-motion` 时全部降级为 0ms 或仅透明度变化。

---

## 8. 图标规范

- **结构图标一律矢量**：管理端 `@element-plus/icons-vue`（统一线性风格，stroke 1.5px）；小程序用内联 SVG 或本地 PNG 图标（tab 图标）。
- **禁用 emoji 图标清单（当前需替换）**：管理端侧栏 logo、登录页 logo（`Grid` 占位图标 → 稻穗 SVG）；小程序首页搜索 `🔍`、热销 `🔥`、新品 `🆕`、banner 占位 `🍊🥬🌾`。
- Logo：管理端侧栏与登录页统一使用**稻穗线性 SVG**（两束稻穗 + 圆点谷粒，品牌绿/白双态）+ 「农产品销售」白字；小程序首页品牌区用同款 SVG。
- 图标尺寸 token：管理端 16/20/24/32px；小程序 32/40/48/64rpx；同一层级统一描边与风格。
- 可访问性：纯装饰图标 `aria-hidden="true"`；承载含义的图标配文字/`aria-label`；图标按钮命中区 ≥ 24px（管理端）/ ≥ 88rpx（小程序）。

---

## 9. 图表规范（ECharts，管理端 Dashboard）

依据 `--domain chart` 查询（Performance vs Target → Bullet/Gauge 建议；构成类 → 饼图带百分比标签；趋势类 → 柱线双轴 + tooltip；实时类 → 需 pause/降采样）：

1. **系列色板（固定顺序）**：`#15803D, #22C55E, #A16207, #2563EB, #6B7280, #DC2626`（品牌绿起头，丰收金用于销售额，蓝仅作扩展色，禁止回退 Element 默认蓝绿橙）；
2. **网格线** `#E5E7EB`，坐标轴文字 `#6B7280` 12px，轴线隐藏；
3. **柱状**：品牌绿 `#15803D`，`borderRadius: [4,4,0,0]`，空态灰 `#E5E7EB`；
4. **折线**：2px 平滑 `#15803D`，面积渐变 `rgba(34,197,94,0.15)`；
5. **饼图**：**标签必须显示名称 + 百分比文字**（不以颜色为唯一区分，`label.formatter: '{b}: {d}%'`），图例 + tooltip 必须开启；`borderRadius: 4`；
6. **多 KPI 对比**可选用 Bullet Chart（目标刻度 + 区间色），色带参考 `#FFCDD2 / #FFF9C4 / #C8E6C9`（bad/ok/good），性能条 `#15803D`，目标线深色 3px 标记；
7. 无障碍：tooltip/图例/标签文本三者至少其二存在；键盘可达时 hover 信息须在焦点上同样可见。

---

## 10. 平台适配落地

### 10.1 管理端（Element Plus 主题覆盖）

在 `main.ts` 引入顺序之后追加全局 token（`styles/theme.css`）：

```css
:root {
  --el-color-primary: #15803D;
  --el-color-primary-light-3: #4D9E6D;
  --el-color-primary-light-5: #8ABFA1;
  --el-color-primary-light-7: #C7DFD0;
  --el-color-primary-light-8: #DCEFE3;
  --el-color-primary-light-9: #F0FDF4;
  --el-color-primary-dark-2: #14532D;
  --el-color-success: #16A34A;
  --el-color-warning: #D97706;
  --el-color-danger: #DC2626;
  --el-color-error: #DC2626;
  --el-color-info: #6B7280;
  --el-border-radius-base: 8px;
  --el-font-size-base: 14px;
  --el-text-color-primary: #1F2937;
  --el-text-color-regular: #6B7280;
  --el-text-color-secondary: #6B7280;
  --el-text-color-placeholder: #9CA3AF;
  --el-border-color: #E5E7EB;
  --el-border-color-light: #BBF7D0;
  --el-border-color-lighter: #EDEFF2;
  --el-fill-color-blank: #FFFFFF;
  --el-fill-color-light: #E8F0F1;
  --el-bg-color-page: #F0FDF4;
}
```

- 布局：侧栏 200px（`<768px` 折叠为 64px 图标模式）；顶栏高 60px 白底 `#E5E7EB` 底边框；内容区 `#F0FDF4` 内边距 20px。
- `el-card` 覆盖：圆角 12px、描边 `#BBF7D0`、shadow=never（配 shadow-sm）。
- `styles/admin-table.css` 中所有裸色值替换为上述 token 对应色：表头 `#E8F0F1`/`#475569`、行 hover `#F0FDF4`、价格 `#A16207`、评分 `#A16207`、空态 `#9CA3AF`。

### 10.2 小程序（uni.scss / pages.json / theme.json 三处同步）

`uni.scss` 关键映射：

```scss
$uni-color-primary: #15803D;
$uni-color-success: #16A34A;
$uni-color-warning: #D97706;
$uni-color-error: #DC2626;
$uni-text-color: #1F2937;
$uni-text-color-grey: #6B7280;
$uni-text-color-placeholder: #9CA3AF;
$uni-text-color-disable: #9CA3AF;
$uni-bg-color: #FFFFFF;
$uni-bg-color-grey: #F0FDF4;        // 页面背景升级
$uni-bg-color-hover: #F0FDF4;
$uni-bg-color-muted: #E8F0F1;       // 新增 muted
$uni-border-color: #E5E7EB;
$uni-border-color-brand: #BBF7D0;   // 新增品牌描边
$uni-border-radius-base: 16rpx;
$uni-font-size-base: 28rpx;
$uni-opacity-disabled: 0.4;
```

`pages.json`：`globalStyle.navigationBarBackgroundColor = #15803D`、`navigationBarTextStyle = white`、`backgroundColor = #F0FDF4`；`tabBar.color = #9CA3AF`、`selectedColor = #15803D`、`backgroundColor = #FFFFFF`。

`theme.json`：light/dark 的 `navBgColor`、`bgColorTop`、`tabSelectedColor` 全部改 `#15803D`（dark 用 `#22C55E`）；`bgColor` 改 `#F0FDF4`/`#181818`。

`App.vue` 全局样式：`.card` 圆角 24rpx、描边 `#BBF7D0`、阴影 `shadow-sm`、边距 24rpx；`.btn-primary` 改 `#15803D`、胶囊 44rpx、高 88rpx；`.price` 改 `#A16207`；`.divider` 改 `#E5E7EB`；`.empty-state` 字 `#9CA3AF`；页面 `page` 背景 `#F0FDF4`。

---

## 11. 响应式与安全区

### 11.1 管理端

- 断点：`<768px`（侧栏折叠）、`768–1200px`（标准）、`>1200px`（宽屏栅格拉伸）；
- Dashboard 统计卡：`lg:6`→`md:12`→`<768px:24`（每行 4→2→1 张）；
- 表格容器 `overflow-x: auto`，固定操作列 `fixed="right"` 保留；
- 图表卡片高度固定 240px，容器 resize 自动重绘（BaseChart 已用 ResizeObserver）。

### 11.2 小程序

- 全部使用 rpx（750 设计稿）自适应；横屏/平板下页面内容区 `max-width: 900rpx; margin: 0 auto`；
- 固定底栏（结算条、TabBar 之外的悬浮按钮）加 `padding-bottom: env(safe-area-inset-bottom)`；顶部导航由原生承担，勿自绘遮挡；
- 375px 小屏与 iPhone 底部小黑条场景必须验证。

---

## 12. 可访问性验收清单（页面改造后逐项核对）

- [ ] 正文/次要文字对比度 ≥ 4.5:1（对照 §2.6 色表，禁止引入新色）；
- [ ] 所有可交互元素键盘可达，focus 可见（管理端 2px `#22C55E` ring）；
- [ ] 表单有可见 label；错误提示带图标 + 文案，且保留已填内容；
- [ ] 状态标签颜色 + 文字双重表达；图标按钮有 `aria-label`/文本；
- [ ] 装饰性图标 `aria-hidden="true"`；图片有 `alt`；
- [ ] 小程序触控目标 ≥ 88rpx、间距 ≥ 16rpx；
- [ ] `prefers-reduced-motion` 降级；无自动播放媒体（banner 轮播保留手滑，autoplay 需可暂停）；
- [ ] 深色模式（theme.json dark）独立核验对比度；
- [ ] 图表：饼图带百分比标签 + 图例 + tooltip（三项至少两项）；
- [ ] 全局无 emoji 图标残留（grep `🌾|🔍|🔥|🆕|🍊|🥬|🌿` 为空）；
- [ ] 全局无旧色残留（grep `1a7bf5|4CAF50|ff6b00|007aff|FF9800|f56c6c|409EFF|67C23A|E6A23C` 为空）。

---

## 13. 页面级速查

### 管理端（9 页）

| 页面 | 改造要点 |
|---|---|
| LayoutView | 侧栏 `#14532D`、logo 换稻穗 SVG、激活项 `#15803D`+左侧 3px 指示条、顶栏 60px 白底、内容区 `#F0FDF4` 20px；`<768px` 折叠 |
| LoginView | 背景 `#F0FDF4`，居中卡片 380px 白底圆角 12px，logo 换稻穗 SVG，输入框加 User/Lock 图标，主按钮全宽品牌绿 |
| DashboardView | 统计卡响应式（4→2→1 列）、卡片白底 1px `#BBF7D0` 描边、数值 20px 600；图表按 §9 配色与标签 |
| ProductListView | 价格 `#A16207`、状态 tag 按 §2.3、表头/斑马纹/hover 按 §7.4、搜索卡+表格卡结构 |
| CategoryListView | 树节点状态 tag 规范化、编辑面板表单按 §7.2 |
| OrderListView | 状态 tag 映射（待付款 warning/待发货 primary/待收货 success/已完成 info/已取消 info/售后中 danger）、金额 `#A16207` |
| UserListView | 头像 32px 圆形、状态 switch 品牌绿、弹窗表单规范 |
| ReviewListView | 评分星 `#A16207`、回复状态 tag、内容两行截断保留 |
| BannerListView | 图片卡片化、状态 switch 品牌绿 |

### 小程序（12 页 + 6 组件）

| 页面/组件 | 改造要点 |
|---|---|
| 首页 index | 搜索栏品牌绿底胶囊白字、轮播圆角 24rpx 边距 24rpx（placeholder 文案去 emoji）、分类宫格图标底 `#F0FDF4`、区块标题 32rpx 600 `#14532D`、去 emoji |
| 分类 category | 左侧分类栏激活态品牌绿、商品列表卡片化 |
| 商品 product | 主图、价格 `#A16207` 36rpx、加购按钮品牌绿胶囊 88rpx、sku 选中态 `#15803D` 边框 |
| 购物车 cart | 结算条固定底部 + 安全区、选中 checkbox 品牌绿、金额 `#A16207` |
| 我的 mine | 头像区品牌绿底、菜单列表分组卡片化 |
| 地址 address | 表单 label 可见、默认标签规范 |
| 订单列表/详情/确认/支付成功 | 状态标签统一、按钮规范、安全区 |
| 评价列表/写评价 | 星级 `#A16207`、评分标签、表单规范 |
| NavBar/ProductCard/StarRating/AddressPicker/InputEntry/AppFooter | 统一 token：卡片 24rpx、价格 accent、标签浅底深字、触控 ≥88rpx |

---

## 14. 交付前检查表

1. 两端品牌色唯一（grep `1a7bf5|4CAF50|ff6b00|007aff|FF9800|f56c6c|409EFF|67C23A|E6A23C` 应为 0）；
2. 无 emoji 图标残留；
3. 全部标签为浅底深字且色值来自 §2.3；
4. 小程序触控目标与安全区达标；
5. `vue-tsc` / 构建无新错误；两端 dev server 热更新正常（**不重启进程**）；
6. 管理端与小程序截图核对（375px / 1440px）。
