# agsell-frontend

农产品销售管理系统 · 管理端前端（Vue 3 + TypeScript + Vite + Element Plus + ECharts）

## 技术栈

- **Vue 3** `<script setup>` + **Pinia** + **Vue Router**（路由懒加载）
- **Element Plus**：`unplugin-vue-components` 按需引入（模板 `el-*` 组件自动注册），样式走完整主题 CSS
- **ECharts**：`echarts/core` 按需注册（Bar / Line / Pie + Grid / Legend / Title / Tooltip / Canvas）
- **Axios**：统一请求/响应拦截，`/api` 由 Vite 代理到后端

## 目录结构

```
src/
├── api/          # 接口封装（admin / upload）
├── components/   # BaseChart（图表封装）、admin/（PageHeader、KpiPanel 等复用组件）
├── router/       # 路由与登录守卫
├── stores/       # Pinia（管理员状态）
├── styles/       # theme.css（设计系统 v3 token 与 EP 覆盖）、admin-table.css（表格/分页）
├── types/        # 全局类型
├── utils/        # axios 实例
└── views/admin/  # 登录、布局与 8 个管理页面
```

设计规范见仓库根 `doc/design-system-v3.md`。

## 常用命令

```bash
npm install      # 安装依赖
npm run dev      # 启动开发服务器（端口 5173，/api 代理到 http://localhost:8080）
npm run build    # 类型检查 + 生产构建
npm run lint     # oxlint + eslint 检查并修复
npm run format   # prettier 格式化
```

## 说明

- 管理端 token 存于 `localStorage.admin_token`，请求头 `Authorization: Bearer <token>`。
- 接口约定：成功 `code: 0`；列表字段 `records` + `total`。
- `src/components.d.ts` 由 unplugin-vue-components 自动生成，勿手动修改（已 gitignore）。
