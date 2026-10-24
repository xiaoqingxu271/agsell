# agsell — 农产品/特产电商销售系统

[![CI](https://github.com/xiaoqingxu271/agsell/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/xiaoqingxu271/agsell/actions/workflows/ci.yml)

一套**三端一体**的农产品电商平台毕业设计项目：产地直供 + 全链路溯源 + AI 智能客服，打通"浏览 → 下单 → 支付 → 履约 → 售后"完整购物闭环。

| 组成 | 技术栈 | 说明 |
|---|---|---|
| 后端 API | Spring Boot 4.1.1 · Java 21 · MyBatis-Plus · MySQL 8 · Redis · JWT · Knife4j | 单体服务，39 个接口控制器，27 张数据表 |
| 管理后台 | Vue 3 · Element Plus · Pinia · ECharts · Vite | 商品/订单/用户/营销/AI 知识库与转人工工单/系统 17 个功能页（另含登录页与框架布局页） |
| 用户端 | uni-app（H5 + 微信小程序双端） | 28 个页面，完整购物与售后流程 |
| AI 智能客服 | FastAPI · LangGraph · RAG（ChromaDB + bge-small-zh + MySQL 全文 BM25）· OpenAI 兼容 LLM | 独立 Python 服务，SSE 流式回复 + 评价情感分析 |

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
│  23 张表    │  │ token/秒杀库存/优惠券 │
└────────────┘  └────────────┘
```

**AI 客服流式链路**：小程序 → Java `/ai/chat/stream`（SseEmitter 转发）→ Python `/v1/chat/stream`（LangGraph 双通道流）→ 高置信规则前置后由大模型规划工具（FAQ 检索 / 商品导购 / 订单 / 物流 / 售后 / 转人工）→ 取证结果交生成节点，回复逐字下发，业务查询阶段实时提示。

## 功能总览

**用户端（小程序/H5）**：首页运营位 · 分类浏览 · 搜索（热词/高亮）· 商品详情（口碑摘要条：好评率/好评关键词 + 收藏）· 我的收藏 · 购物车 · 下单（运费预览）· 模拟支付 · 订单状态机（待付款→待发货→待收货→已完成 / 超时自动取消）· 评价（带情感标签）· 售后退款 · 优惠券领取与核销 · 秒杀 · 产地溯源（扫码查看生产记录）· AI 客服（流式逐字回复 + 智能导购推荐）· 消息中心（发货/售后处理结果站内信，首页铃铛未读红点）

**管理后台**：数据仪表盘（ECharts，含评价口碑分布饼图与好评关键词）· 商品/规格/分类管理 · 订单发货 · 用户管理（RBAC：超级管理员/运营专员）· 评价管理（回复/口碑分析同步）· 轮播图运营 · 优惠券活动 · 秒杀活动 · 售后处理 · 溯源信息维护 · 热词管理 · **AI 知识库维护**（FAQ 增删改查 + 两个向量索引的状态与全量重建，Java 鉴权代理转 Python）· 操作日志（SpEL 中文化）· 系统配置 · 待办提醒（待发货/低库存/待售后/待处理 AI 工单）· **AI 转人工工单**（列表/处理/备注，待处理排前）

**AI 客服**：高置信规则前置（命中即短路，省一次大模型往返）→ 工具编排（`bind_tools` 有界循环：步数 2、调用 4、(工具,参数) 去重、空结果反思重试一次）→ 工具执行（FAQ 三路检索 / 商品语义召回+个性化重排 / 订单 / 物流 / 售后 / 转人工，闸门都在执行器里；触发转人工即回调 Java 落工单，管理后台「AI 转人工工单」页跟进）→ 生成回复（知识问答附"依据"引用标签，由服务端按检索命中结构化下发）；多轮会话记忆（SQLite 持久化）、上游不支持 function calling 时自动降级为"分类 + 单工具"、LLM 限流自动重试与降级话术、SSE 流式输出。

**评价情感分析**：领域词库 + 否定翻转 + 程度副词 + 评分先验的规则分析器（Python `/v1/sentiment/batch`，确定性输出零 LLM 成本）；管理端触发同步回写 review 表，商品详情接口聚合返回好评率与好评关键词标签。

**RAG 检索与个性化**：知识库三路召回（向量语义 + MySQL ngram 全文 BM25 + 关键词，RRF 融合；词面通道过校验保拒答语义，单通道故障自动退场）；导购个性化重排的类目偏好由六路信号合并——订单 / 收藏 / 好评 / 加购 / 浏览（时间衰减）/ 搜索词（关键词映射品类），多轮追问的召回串自动携带上文（历史感知改写）。

**工程化亮点**：乐观锁防超卖（支付/秒杀 Redis 预扣）、订单超时/优惠券过期定时任务、操作日志切面、并发集成测试、docker-compose 一键编排。

## 快速开始（Docker Compose 一键启动）

前置：Docker + Docker Compose，[Agnes AI](https://apihub.agnes-ai.com) 免费 API Key（AI 客服用）。

```bash
# 1. 配置环境变量
cp .env.example .env          # 按下表把占位值换成真实值（AI_INTERNAL_KEY 与 ai-service/.env 保持一致）
cp ai-service/.env.example ai-service/.env   # 填入 AGNES_API_KEY

# 2. 一键构建启动（mysql 自动执行 sql/ 下全部建表与种子脚本）
docker compose up -d --build

# 3. 初始化 AI 知识库（建 ai_faq 表 + 种子 FAQ + 向量索引）
docker compose exec ai-service python scripts/init_faq.py --index
```

# 4. （可选）导入商品域全量演示数据：10 个一级 / 42 个二级分类（Iconify 图标）、
#    202 个商品（Pexels 实拍图 + 规格 + 详情图）、5 张轮播图，以及绑定这些商品的评价 /
#    溯源档案 + 生产记录 / 秒杀活动。图片走阿里云 OSS 公共读 URL，脚本由
#    tools/seed-catalog（采集→上传→生成）产出，会先清空商品域及其依赖表
mysql -h 127.0.0.1 -P 3307 -uroot -p agsell < sql/seed_catalog.sql
docker compose exec mysql mysql -uroot -p"$$MYSQL_ROOT_PASSWORD" agsell < /sql/seed_catalog.sql

# 5. （可选）导入演示用户 13800138000 与收货地址（评价的展示主体；幂等，可重复执行）
mysql -h 127.0.0.1 -P 3307 -uroot -p agsell < sql/demo_data.sql
docker compose exec mysql mysql -uroot -p"$$MYSQL_ROOT_PASSWORD" agsell < /sql/demo_data.sql

根目录 `.env` 需要的变量：

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `JWT_SECRET` | JWT 签名密钥（64 字节） |
| `WECHAT_MINIAPP_APPID` / `WECHAT_MINIAPP_SECRET` | 微信小程序凭据（无则登录走游客模式） |
| `ALIYUN_OSS_ENDPOINT` / `ACCESS_KEY_ID` / `ACCESS_KEY_SECRET` / `BUCKET_NAME` | 阿里云 OSS（图片上传） |
| `AI_INTERNAL_KEY` | Python 回调 Java `/api/ai/internal/**` 的服务间密钥，须与 `ai-service/.env` 的 `JAVA_INTERNAL_KEY` 一致；`application.yaml` 已无默认值，缺失则 backend 启动失败 |

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
# 后端：单元 + 并发集成测试（秒杀/优惠券超卖/支付并发防重复、售后全流程、短信/评价边界、
# AI 工单/站内信/收藏），38 个测试类；
# 2026-10-24 实测 `./mvnw test` = Tests run: 316, Failures: 0, Errors: 0, Skipped: 1
# （Skipped 是 500 并发抢 180 库存那条放大档压测，手动全量压测：./mvnw verify -Dstress.test=true）
./mvnw test

# AI 服务：图路由 / RAG / 会话记忆 / SSE 流式接口 / 情感分析 / 个性化重排 / 工具编排 / 知识库管理接口 / 转人工工单
# 2026-10-24 实测：229 passed（Python 3.14.6 + requirements.lock.txt）
cd ai-service && .venv/Scripts/python -m pytest tests -q

# 管理后台：vitest 11 passed；oxlint + eslint 0 error；vue-tsc 无错；vite build 通过
cd agsell-frontend && npm run test && npm run lint && npm run type-check && npm run build
```

> `.venv` 与创建它的解释器路径绑定：若换机器、换 Python 安装位置，或在别的目录下创建过，
> `.venv/Scripts/python.exe` 会报 `did not find executable`（本项目 2026-10-17 就踩到过）。
> 重建只需三步，不需要改代码：
> ```bash
> cd ai-service && rm -rf .venv && python -m venv .venv
> .venv/Scripts/python -m pip install -r requirements.txt
> # 本地跑实验还要覆盖 DATABASE_URL（.env 里的 host 是 Docker 专用的 host.docker.internal）
> ```

### 量化实验（论文数据）

七项核心指标的实测数据与结论见 [doc/量化实验报告.md](doc/量化实验报告.md)：秒杀高并发、意图识别准确率（含 hold-out 集与规则/大模型/生产三通道消融）、RAG 检索命中率（知识库全量 280 条覆盖）、AI 流式响应性能（含导购分支首 token 延迟复测）、反幻觉闸门（知识库 + 导购两道，附阈值-风险曲线）、智能导购召回与个性化、智能体工具编排（function calling 选型准确率与域外探针）。所有 JSON 存档带 `measured_at` 与 `git_commit`，正文数字与存档同源。

```bash
# 秒杀压测（防超卖与 QPS，需本地 MySQL + Redis）
# 注意：类名是 SeckillConcurrencyTest（不是 *IT，已纳入 mvnw verify）；
# 500 并发档带 @EnabledIfSystemProperty(stress.test=true)，不加该参数不会执行
./mvnw test -Dtest=SeckillConcurrencyTest -Dstress.test=true

# AI 侧五项实验（脚本在 ai-service/scripts/eval_*.py）
# 本地跑需覆盖 DATABASE_URL（.env 里的 host 是 Docker 专用的 host.docker.internal）
cd ai-service
export DATABASE_URL="mysql+pymysql://root:<密码>@127.0.0.1:3306/agsell?charset=utf8mb4"
.venv/Scripts/python scripts/eval_intent.py --testset holdout --mode keyword    # 纯规则通道
.venv/Scripts/python scripts/eval_intent.py --testset holdout --mode llm_only --workers 1   # 纯大模型
.venv/Scripts/python scripts/eval_intent.py --testset holdout --mode ll --workers 1         # 生产路径
.venv/Scripts/python scripts/eval_rag.py        # RAG 命中率（知识库全覆盖 280 条）
.venv/Scripts/python scripts/eval_refusal.py    # 拒答率 + 导购闸门泄漏率
.venv/Scripts/python scripts/eval_recommend.py  # 导购召回三通道 + 个性化重排回放
# 流式延迟需先起服务：.venv/Scripts/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
.venv/Scripts/python scripts/eval_latency.py --rounds 2
```

## 目录结构

```
├── src/main/java/com/lichun/agsell/   # Java 后端（controller/{admin,user} 按端分包）
├── sql/                                # 建表脚本（按模块拆分）+ migration/ 增量脚本
├── agsell-frontend/                    # 管理后台（Vue 3 + Element Plus）
├── agsell-uniapp/                      # 用户端（uni-app，H5 + 微信小程序）
├── ai-service/                         # AI 智能客服（FastAPI + LangGraph + RAG）
│   ├── app/agent/                      #   规则前置 / 工具 schema / 执行器 / 有界循环 / 图编排
│   ├── app/rag/                        #   向量库、FAQ 检索与商品召回索引（FAQ 280 条 / 商品 202 条）
│   ├── app/api/admin_kb.py             #   知识库管理接口（Java 代理唯一后端）
│   └── scripts/init_faq.py             #   知识库初始化脚本（日常维护走管理后台「AI 知识库」页）
├── doc/                                # 需求规格说明书 + 14 份模块开发文档 + 设计规范
└── docker-compose.yml                  # mysql + redis + backend + frontend + ai-service
```

## 文档索引

- [需求规格说明书](doc/需求文档.md) —— 系统边界、角色权限、功能需求、数据模型
- [MASTER 设计规范](doc/MASTER.md) —— 双端统一视觉体系（品牌绿 #15803D）
- [AI 智能客服技术设计](doc/AI智能客服模块-技术栈设计.md) 及[阶段五测试报告](doc/AI智能客服模块-阶段五测试报告.md)
- 各模块开发文档（商品/订单/支付/秒杀/优惠券/售后/溯源/搜索/系统管理等）见 `doc/`

> 说明：支付为模拟实现（需求边界明确预留微信/支付宝接口）；物流为状态模拟流转。
