# agsell AI 智能客服服务（ai-service）

独立 Python AI 服务：**FastAPI + LangGraph + Agnes AI（免费大模型）+ RAG 知识库 + 业务工具回调 Java**，由 Java 主系统（agsell Spring Boot）通过 HTTP 调用。

## 快速开始（Windows / Python 3.14）

```
# 1. 创建并激活虚拟环境
python -m venv .venv
.\.venv\Scripts\Activate.ps1

# 2. 安装依赖（清华镜像，本机全局 pip 源已失效）
python -m pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 3. 配置密钥（复制模板并填写 AGNES_API_KEY；JAVA_INTERNAL_KEY 需与 Java 侧一致）
Copy-Item .env.example .env

# 4. 初始化 FAQ 知识库（建表 + 种子数据 + 重建索引；首次会经 HF 镜像下载 Embedding 模型 ~95MB）
python scripts\init_faq.py --index

# 5. 启动服务（阶段三起依赖 Java 服务已在 8080 运行）
uvicorn app.main:app --port 8000
```

> 依赖模型下载：`.env` 已配置 `HF_ENDPOINT=https://hf-mirror.com`（app/config.py 会自动导出到进程环境变量），官方 huggingface.co 在本机不可达，勿改回。

## 接口一览

| 方法     | 路径                       | 说明                                                                           |
| ------ | ------------------------ | ---------------------------------------------------------------------------- |
| GET    | `/health`                | 健康检查                                                                         |
| POST   | `/v1/chat`               | 客服对话（意图路由 + 业务工具 + RAG + Agnes）：`{"sessionId":"s_001","message":"我的订单到哪了","userId":2095531860995977217}` |
| GET    | `/v1/admin/kb/stats`     | 知识库统计（FAQ 数 / 索引数 / 模型）                                                      |
| POST   | `/v1/admin/kb/index`     | 全量重建索引（MySQL → 向量化 → Chroma）                                                 |
| GET    | `/v1/admin/kb/faqs`      | FAQ 列表（分页 / 分类）                                                              |
| POST   | `/v1/admin/kb/faqs`      | 新增 FAQ                                                                       |
| PUT    | `/v1/admin/kb/faqs/{id}` | 更新 FAQ（改后需重建索引生效）                                                            |
| DELETE | `/v1/admin/kb/faqs/{id}` | 删除 FAQ                                                                       |
| GET    | `/docs`                  | Swagger 文档                                                                   |

## 架构（阶段三）

```
用户问题（Java 透传 userId）
      │
      ▼
intent 节点：LLM 意图分类 + 订单号正则 + 关键词兜底
      │
      ▼  route_by_intent 条件路由
      ├─ faq ───────► faq_retrieve（双路召回）──► generate ──► END
      ├─ order ─────► order_query（httpx 回调 Java）──► generate ──► END
      ├─ logistics ─► logistics_query（httpx 回调 Java）──► generate ──► END
      ├─ after_sales► after_sales_query（httpx 回调 Java）──► generate ──► END
      ├─ smalltalk ─► smalltalk 节点（闲聊）──► END
      └─ human ─────► human_fallback（固定话术转人工）──► END
```

* **意图标签**：`faq / order / logistics / after_sales / smalltalk / human`。
* **服务间鉴权**：Python 回调 Java 内部接口携带 `X-Internal-Key`（= `JAVA_INTERNAL_KEY`，与 Java 侧 `ai-service.internal-key` 一致）；Java 侧 `AiInternalAuthInterceptor` 校验，与用户 JWT 体系隔离。
* **Java 接入（阶段四）**：`POST /api/ai/chat`（Java AiChatController）→ 白名单放行游客、登录用户解析 JWT 取 userId 透传 → Hutool 60s 超时转发 Python `/v1/chat`。
* **多轮上下文（阶段四）**：LangGraph 默认 MemorySaver checkpointer，`ainvoke(config={"configurable":{"thread_id":sessionId}})` 按会话 ID 记忆；sessionId 由小程序页面级生成。
* **隐私最小化**：Java 返回的订单 VO 刻意不含收货人 / 电话 / 地址；`order/detail` 强制校验订单归属（他人订单返回 40400）。
* **未登录处理**：`userId` 为空时订单 / 物流 / 售后节点返回引导话术（建议登录或拨打客服电话），不走 Java 回调。
* **FAQ 双路召回**：① Chroma 向量召回（bge-small-zh-v1.5, cosine）② 关键词精确命中（ai_faq.keywords，每个 +0.5 分），合并去重回填答案。
* **图结构**：`START → intent → 条件路由（6 分支）`（LangGraph 1.2，代码在 `app/agent/`）。

## Java 侧内部接口（阶段三新增）

| 方法 | 路径（context-path=/api） | 请求体 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/ai/internal/order/list` | `{"userId":2095531860995977217}` | 最近 5 笔订单（状态文本映射） |
| POST | `/api/ai/internal/order/detail` | `{"userId":..., "orderNo":"AGS..."}` | 订单详情 + 商品快照 + 物流字段（强制归属校验） |
| POST | `/api/ai/internal/after-sales/list` | `{"userId":...}` | 售后记录（类型 / 状态 / 原因文本映射） |

> 鉴权：请求头 `X-Internal-Key: agsell-ai-internal-2026`（生产环境务必更换）。
> 路径说明：Spring MVC 拦截器 `addPathPatterns` 匹配的是 servlet path（不含 context-path），故注册为 `/ai/internal/**`；JWT 白名单用 `requestURI`（含 context-path），故写 `/api/ai/internal`。

## 目录结构

```
ai-service/
├── app/
│   ├── main.py              # FastAPI 入口（/health、/v1/chat 透传 userId、挂载知识库路由）
│   ├── config.py            # .env 配置（pydantic-settings + load_dotenv 导出环境变量）
│   ├── db.py                # SQLAlchemy 引擎/会话（复用 agsell 主库）
│   ├── prompts.py           # 系统提示词模板（FAQ / 工具 / 闲聊 / 转人工）
│   ├── schemas/chat.py      # Pydantic 请求/响应模型（userId 可选）
│   ├── models/faq.py        # ai_faq ORM
│   ├── repositories/faq_repo.py  # FAQ 建表 + CRUD
│   ├── rag/
│   │   ├── vectorstore.py   # Chroma 封装（rebuild 全量重建 / query 余弦相似度）
│   │   └── retriever.py     # 双路召回检索器（向量 + 关键词，去重排序）
│   ├── tools/java_api.py    # httpx 回调 Java 三个内部接口 + 紧凑 JSON 格式化
│   ├── agent/
│   │   ├── state.py         # AgentState（messages + intent/order_no/userId/tool_result/...）
│   │   ├── intent.py        # classify_intent（LLM JSON + 订单号正则 + 关键词兜底表）
│   │   ├── nodes.py         # intent / faq_retrieve / 业务查询 / generate / smalltalk / human_fallback
│   │   └── graph.py         # LangGraph 图（build_graph(llm, retriever, java_api_module)；阶段四起默认 MemorySaver checkpointer）
│   └── api/admin_kb.py      # 知识库管理接口（stats/index/faqs，X-Internal-Key 鉴权预留）
├── scripts/
│   ├── seed_data.py         # 36 条种子 FAQ（基于 agsell 真实业务规则）
│   └── init_faq.py          # 建表 + 种子写入 + 重建索引（--force/--index）
├── tests/                   # pytest 11 个用例（图路由 + 工具节点 + 双路召回，不依赖网络/LLM）
├── data/                    # Chroma 本地数据（gitignore）
├── requirements.txt
└── .env / .env.example
```

## 已验证结果

| 验证项 | 结果 |
| --- | --- |
| 依赖安装 | chromadb 1.5.9 / fastembed 0.8.0 / sqlalchemy 2.0.54 / langgraph 1.2.11 / langchain-openai 1.6.2 |
| 建表 + 种子 | `ai_faq` 36 条（平台 / 账号 / 商品 / 订单 / 物流 / 售后 / 评价 / 溯源 / 秒杀） |
| 索引 | 36 条入 Chroma（cosine, bge-small-zh-v1.5） |
| 检索召回 | 5 个问题 Top1 全部命中（退款 / 运费 / 发货 / 客服电话 / 坏果） |
| pytest | 11/11 通过（图路由 + 工具节点 + 召回，stub 不依赖网络/LLM） |
| Java 内部接口鉴权 | 无 Key → 40101「内部接口鉴权失败」；带 Key → 正常返回；他人订单 detail → 40400 |
| 端到端对话（阶段三，2026-09-18） | ①"我的订单现在是什么状态？"→ 真实 5 笔订单状态汇总；②"订单 AGS2097951729641160704 到哪了？"→ 待发货 + 引导；③"我申请过退款吗？"→ 4 笔已同意 + 1 笔已拒绝明细；④"你好"→ 闲聊回复；⑤未登录查订单/退款 → 引导登录话术；⑥"客服电话是多少？"→ 400-000-0000 |
| 阶段四联调（2026-09-18） | Java AiChatController + 小程序聊天页全链路 10 用例通过：游客 FAQ 4 问、JWT 登录态订单/售后/物流 3 问、多轮上下文 2 问、空消息边界 1 问（详见 doc 设计文档 §14 联调报告）；uniapp `build:h5` 通过 |
| 阶段五加固（2026-09-18） | ① 会话记忆持久化 SqliteSaver：**跨重启实测**（"我叫小明"→ 重启 →"我是谁"答"你是小明呀"）+ SQLite 落盘 28 条链；② Agnes 限流退避 max_retries=2 + 降级话术（FailingLLM stub 验证不白屏）；③ pytest **19/19**；④ Dockerfile + docker-compose（python:3.14-slim + redis-stack）；⑤ 安全复查：`/v1/chat` 新增 X-Internal-Key 鉴权（无 Key 401/带 Key 200），密钥不入 git，越权/隐私/Prompt 注入逐项通过 |

## 阶段规划

| 阶段 | 内容 | 状态 |
| -- | ---------------------------------------- | ----- |
| 一  | 项目骨架 + Agnes AI 连通                       | ✅ 已完成 |
| 二  | FAQ 知识库（MySQL + Chroma + bge + 双路召回）     | ✅ 已完成 |
| 三  | 业务工具（httpx 回调 Java 查订单 / 物流 / 售后 + 意图路由） | ✅ 已完成 |
| 四  | Java 接入（AiChatController）+ 小程序聊天页        | ✅ 已完成 |
| 五  | 加固（SqliteSaver 持久化 / 重试降级 / Docker / 安全复查） | ✅ 已完成 |

## 启动方式

```bash
# 方式一：本地直跑（开发）
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000

# 方式二：Docker 一键起（需 Docker；内置 redis-stack 可选）
docker compose up -d --build
```

依赖配置见 `.env.example`（复制为 `.env` 填写 AGNES_API_KEY 等）。

> 安全提醒：`.env` 已加入 `.gitignore`，请勿提交；Agnes 免费层可能用数据训练，演示勿传真实手机号 / 地址等隐私；`agsell-ai-internal-2026` 为本地演示密钥，部署前务必更换。
