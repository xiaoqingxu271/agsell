# agsell — 农产品/特产电商销售系统

[![CI](https://github.com/xiaoqingxu271/agsell/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/xiaoqingxu271/agsell/actions/workflows/ci.yml)

一套**三端一体**的农产品电商平台毕业设计项目：产地直供 + 全链路溯源 + AI 智能客服，打通"浏览 → 下单 → 支付 → 履约 → 售后"完整购物闭环。

| 组成 | 技术栈 | 说明 |
|---|---|---|
| 后端 API | Spring Boot 4.1.1 · Java 21 · MyBatis-Plus · MySQL 8 · Redis · JWT · Knife4j | 单体服务，32 个接口控制器，22 张数据表 |
| 管理后台 | Vue 3 · Element Plus · Pinia · ECharts · Vite | 商品/订单/用户/营销/系统 15 个页面 |
| 用户端 | uni-app（H5 + 微信小程序双端） | 23 个页面，完整购物与售后流程 |
| AI 智能客服 | FastAPI · LangGraph · RAG（ChromaDB + bge-small-zh）· OpenAI 兼容 LLM | 独立 Python 服务，SSE 流式回复 + 评价情感分析 |

## 系统架构

```
┌──────────────┐    ┌──────────────┐    ┌────────────────────┐
│ 微信小程序/H5 │    │ 管理后台 Web  │    │  AI 智能客服 (8000) │
│  uni-app     │    │ Vue3+Element │    │  FastAPI+LangGraph  │
└──────┬───────┘    └──────┬───────┘    │  RAG 知识库+记忆     │
       │  HTTP(JWT)        │            └─────────┬──────────┘
       ▼                   ▼                      │ SSE 流式 / 回调业务接口
┌────────────────────────────────────┐ ◄────────┘
│      Java 后端 Spring Boot (8080)  │   X-Internal-Key 服务间鉴权
│  用户/商品/订单/支付/秒杀/优惠券/    │
│  售后/溯源/评价/搜索/统计/系统管理   │
└──────┬──────────────┬──────────────┘
       ▼              ▼
┌────────────┐  ┌────────────┐
│  MySQL 8   │  │   Redis    │
│  20 张表    │  │ token/秒杀库存/优惠券 │
└────────────┘  └────────────┘
```

**AI 客服流式链路**：小程序 → Java `/ai/chat/stream`（SseEmitter 转发）→ Python `/v1/chat/stream`（LangGraph 双通道流）→ 意图识别后路由到 FAQ 检索 / 订单 / 物流 / 售后 / 商品导购查询，回复逐字下发，业务查询阶段实时提示。

## 功能总览

**用户端（小程序/H5）**：首页运营位 · 分类浏览 · 搜索（热词/高亮）· 商品详情（口碑摘要条：好评率/好评关键词）· 购物车 · 下单（运费预览）· 模拟支付 · 订单状态机（待付款→待发货→待收货→已完成 / 超时自动取消）· 评价（带情感标签）· 售后退款 · 优惠券领取与核销 · 秒杀 · 产地溯源（扫码查看生产记录）· AI 客服（流式逐字回复 + 智能导购推荐）

**管理后台**：数据仪表盘（ECharts，含评价口碑分布饼图与好评关键词）· 商品/规格/分类管理 · 订单发货 · 用户管理（RBAC：超级管理员/运营专员）· 评价管理（回复/口碑分析同步）· 轮播图运营 · 优惠券活动 · 秒杀活动 · 售后处理 · 溯源信息维护 · 热词管理 · 操作日志（SpEL 中文化）· 系统配置 · 通知中心

**AI 客服**：意图识别（LLM 分类 + 关键词兜底）→ 条件路由（FAQ / 订单 / 物流 / 售后 / 智能导购 / 闲聊 / 转人工）→ RAG 知识库检索 / 商品搜索（偏好抽取：品类+预算，无命中自动回退热销榜）→ 生成回复；多轮会话记忆（SQLite 持久化）、LLM 限流自动重试与降级话术、SSE 流式输出。

**评价情感分析**：领域词库 + 否定翻转 + 程度副词 + 评分先验的规则分析器（Python `/v1/sentiment/batch`，确定性输出零 LLM 成本）；管理端触发同步回写 review 表，商品详情接口聚合返回好评率与好评关键词标签。

**工程化亮点**：乐观锁防超卖（支付/秒杀 Redis 预扣）、订单超时/优惠券过期定时任务、操作日志切面、并发集成测试、docker-compose 一键编排。

## 快速开始（Docker Compose 一键启动）

前置：Docker + Docker Compose，[Agnes AI](https://apihub.agnes-ai.com) 免费 API Key（AI 客服用）。

```bash
# 1. 配置环境变量
cp .env.example .env          # 若仓库无 .env.example，按下表手写一份
cp ai-service/.env.example ai-service/.env   # 填入 AGNES_API_KEY

# 2. 一键构建启动（mysql 自动执行 sql/ 下全部建表与种子脚本）
docker compose up -d --build

# 3. 初始化 AI 知识库（建 ai_faq 表 + 种子 FAQ + 向量索引）
docker compose exec ai-service python scripts/init_faq.py --index
```

# 4. （可选）导入答辩演示数据：13 条口碑评价（情感预填）/ 溯源档案与生产记录 /
#    进行中的秒杀活动 / 演示用户——全部幂等，可重复执行
mysql -h 127.0.0.1 -P 3307 -uroot -p agsell < sql/demo_data.sql
docker compose exec mysql mysql -uroot -p"$$MYSQL_ROOT_PASSWORD" agsell < /sql/demo_data.sql

根目录 `.env` 需要的变量：

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `JWT_SECRET` | JWT 签名密钥（64 字节） |
| `WECHAT_MINIAPP_APPID` / `WECHAT_MINIAPP_SECRET` | 微信小程序凭据（无则登录走游客模式） |
| `ALIYUN_OSS_ENDPOINT` / `ACCESS_KEY_ID` / `ACCESS_KEY_SECRET` / `BUCKET_NAME` | 阿里云 OSS（图片上传） |

启动后访问：

| 服务 | 地址 |
|---|---|
| 管理后台 | http://127.0.0.1:8088 |
| 后端 API / 接口文档 | http://localhost:8080/api/doc.html |
| AI 客服服务 | http://localhost:8000/health |
| MySQL | 127.0.0.1:3307（容器内 3306） |

**演示账号**：管理后台 `admin / admin123`；小程序端为微信授权登录（开发环境 mock，任意 code 自动注册/登录）。

## 本地开发（不用 Docker）

```bash
# 后端（需本地 MySQL/Redis）
# 首次使用：复制 src/main/resources/application-local.yaml.example 为 application-local.yaml，
# 填入你自己的数据库/微信/OSS 密钥（该文件已被 .gitignore 忽略，不会提交）
./mvnw spring-boot:run

# 管理后台
cd agsell-frontend && npm install && npm run dev

# 用户端
cd agsell-uniapp && npm install
npm run dev:h5          # H5 浏览器调试
npm run dev:mp-weixin   # 微信开发者工具导入 dist/dev/mp-weixin
# API 地址改 agsell-uniapp/.env 的 VITE_API_BASE_URL

# AI 服务（Python 3.14）
cd ai-service
python -m venv .venv && .venv/Scripts/pip install -r requirements.txt   # Windows
python scripts/init_faq.py --index   # 首次：初始化 FAQ 知识库
uvicorn app.main:app --port 8000 --reload
```

## 测试

```bash
# 后端：单元 + 并发集成测试（秒杀/优惠券超卖/支付并发防重复、售后全流程、短信/评价边界），共 29 个测试类 226+ 用例
./mvnw test

# AI 服务：图路由 / RAG / 会话记忆 / SSE 流式接口 / 情感分析
cd ai-service && .venv/Scripts/python -m pytest tests -q
```

### 量化实验（论文数据）

四项核心指标的实测数据与结论见 [doc/量化实验报告.md](doc/量化实验报告.md)：

```bash
# 秒杀压测（100/500 并发 + HTTP 层，防超卖与 QPS，需本地 MySQL/Redis）
./mvnw test "-Dtest=SeckillConcurrencyIT#concurrent_100users_vs_50stock+concurrent_500users_vs_200stock" -DfailIfNoTests=false

# 意图识别准确率 / RAG 命中率 / AI 流式延迟（脚本在 ai-service/scripts/eval_*.py）
cd ai-service && .venv/Scripts/python scripts/eval_intent.py --mode keyword
```

## 目录结构

```
├── src/main/java/com/lichun/agsell/   # Java 后端（controller/{admin,user} 按端分包）
├── sql/                                # 建表脚本（按模块拆分）+ migration/ 增量脚本
├── agsell-frontend/                    # 管理后台（Vue 3 + Element Plus）
├── agsell-uniapp/                      # 用户端（uni-app，H5 + 微信小程序）
├── ai-service/                         # AI 智能客服（FastAPI + LangGraph + RAG）
│   ├── app/agent/                      #   意图识别 / 路由 / 节点 / 图编排
│   ├── app/rag/                        #   向量库与 FAQ 检索
│   └── scripts/init_faq.py             #   知识库初始化脚本
├── doc/                                # 需求规格说明书 + 14 份模块开发文档 + 设计规范
└── docker-compose.yml                  # mysql + redis + backend + frontend + ai-service
```

## 文档索引

- [需求规格说明书](doc/需求文档.md) —— 系统边界、角色权限、功能需求、数据模型
- [MASTER 设计规范](doc/MASTER.md) —— 双端统一视觉体系（品牌绿 #15803D）
- [AI 智能客服技术设计](doc/AI智能客服模块-技术栈设计.md) 及[阶段五测试报告](doc/AI智能客服模块-阶段五测试报告.md)
- 各模块开发文档（商品/订单/支付/秒杀/优惠券/售后/溯源/搜索/系统管理等）见 `doc/`

> 说明：支付为模拟实现（需求边界明确预留微信/支付宝接口）；物流为状态模拟流转。
