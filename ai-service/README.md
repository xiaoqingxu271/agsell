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
| GET    | `/v1/admin/kb/stats`     | 知识库统计（FAQ 数 / FAQ 索引数 / 商品索引数 / 模型）                                          |
| POST   | `/v1/admin/kb/index`     | 全量重建 FAQ 索引（MySQL → 向量化 → Chroma）                                             |
| POST   | `/v1/admin/kb/products/index` | 全量重建商品召回索引（兜底修复用，日常靠下面的 upsert）                                                |
| POST   | `/v1/admin/kb/products/upsert`  | 商品变更后的增量同步：`{"productIds":[...]}`，在售→写入索引、查不到（下架/删除）→从索引除名                      |
| GET    | `/v1/admin/kb/faqs`      | FAQ 列表（分页 / 分类）                                                              |
| POST   | `/v1/admin/kb/faqs`      | 新增 FAQ，**201** + `{"id":..,"indexed":..}`（`indexed=false` = 已存库但索引没跟上）         |
| PUT    | `/v1/admin/kb/faqs/{id}` | 更新 FAQ（PATCH 语义，只送非 null 字段；置为停用则自动从索引下架）；返回 `indexed`，含除名成功                  |
| DELETE | `/v1/admin/kb/faqs/{id}` | 删除 FAQ（同时从索引除名）                                                               |
| GET    | `/docs`                  | Swagger 文档                                                                   |

> 管理端七个接口只允许 Java 主系统调用：请求头 `X-Internal-Key` 必须等于 `JAVA_INTERNAL_KEY`；
> **未配置该密钥时一律 503 拒绝（fail-closed）**，不因"本地没配"而放行——这里能改 RAG 的答案来源。
> 浏览器不直连本服务，管理后台走 `agsell-frontend` → Java `/api/admin/ai/kb/*` 代理。

## 架构（智能体版）

```
用户问题（Java 透传 userId / productId）
      │
      ▼
intent 节点：只做高置信规则前置（零 LLM、毫秒级）
      │
      ▼  route_by_intent 条件路由
      ├─ smalltalk ─► smalltalk 节点（纯寒暄直出）──────────────────► END
      ├─ human ─────► human_fallback（固定话术转人工）───────────────► END
      └─ 其余一律 ──► agent 节点（llm.bind_tools 有界工具编排）
                        │  规划：模型自己决定调用哪些工具（一轮可并行多个）
                        │  执行：executors.py（闸门都在这里）
                        │  回灌：观察摘要 → 空结果允许改写重试一次
                        │  上限：AGENT_MAX_STEPS / AGENT_MAX_TOOL_CALLS + (工具,参数) 去重
                        ▼
                     generate 节点（合并本轮全部观察 → 一次答完多个诉求）──► END

注册为 function schema 的 6 个能力：
kb_search（RAG 双路召回）· product_search（向量召回→LIKE→商详→域外拒答→热销）
order_query · logistics_query · after_sales_query · escalate_human
```

* **为什么改造**：旧图是"意图 argmax → 固定单分支 → 调一个工具"，一句"怎么申请退款？另外有没有 50 元以内的橙子"只能答一半；且规则通道在 hold-out 测试集上只有 37.6%（无词面线索子集 23.4%），本来就当不了语义分类器（见 doc/量化实验报告.md 实验二）。现在"选什么工具"交给 function calling，规则层只保留它真正擅长的高置信短路。
* **闸门与编排分离**：已实测生效的把关（知识库阈值拒答、导购 REC_MIN_SCORE=0.47、LIKE 命中优先于域判定、商详 product_qa 兜底）全部留在 `app/agent/executors.py`，重构只改"谁决定调用什么"，不改"调用之后怎么把关"。
* **服务间鉴权**：Python 回调 Java 内部接口携带 `X-Internal-Key`（= `JAVA_INTERNAL_KEY`，与 Java 侧 `ai-service.internal-key` 一致）；Java 侧 `AiInternalAuthInterceptor` 校验，与用户 JWT 体系隔离。
* **Java 接入（阶段四）**：`POST /api/ai/chat`（Java AiChatController）→ 白名单放行游客、登录用户解析 JWT 取 userId 透传 → Hutool 60s 超时转发 Python `/v1/chat`。
* **多轮上下文（阶段四）**：LangGraph 默认 MemorySaver checkpointer，`ainvoke(config={"configurable":{"thread_id":sessionId}})` 按会话 ID 记忆；sessionId 由小程序页面级生成。
* **隐私最小化**：Java 返回的订单 VO 刻意不含收货人 / 电话 / 地址；`order/detail` 强制校验订单归属（他人订单返回 40400）。
* **未登录处理**：`userId` 为空时订单 / 物流 / 售后节点返回引导话术（建议登录或拨打客服电话），不走 Java 回调。
* **FAQ 双路召回**：① Chroma 向量召回（bge-small-zh-v1.5, cosine）② 关键词精确命中（ai_faq.keywords，每个 +0.5 分），合并去重回填答案。
* **图结构**：`START → intent →（smalltalk | human_fallback | agent → generate）`（LangGraph 1.2，代码在 `app/agent/`）。
* **阶段七（低成本加固）**：
  * **意图三级路由**：高置信硬规则前置（人工强诉求 / 纯寒暄毫秒级直出，**不调 LLM**，省往返、省额度）→ LLM 语义分类（prompt 内置 few-shot 易混示例）→ 关键词兜底；寒暄夹带业务词（如"谢谢，退款到哪了"）不前置，一律交 LLM。⚠ 旧记录里"规则通道 103 题 **96.1%**"是在同一批 103 题上反复调参得到的，存在数据泄漏：换到未参与调参的 hold-out 集（109 题）后只有 **37.6%**（语义子集 23.4%），因此智能体改造后规则层不再承担语义分类，只保留 human / 纯寒暄两类高置信判断。口径与复现见 doc/量化实验报告.md §3。
  * **历史窗口截断**：generate / smalltalk 节点按 `HISTORY_MAX_CHARS`（默认 3000 字符）用 `trim_messages` 保留最近对话，system 常驻、从完整 human 轮次起，控制长会话的 token 成本与首 token 延迟。
  * **FAQ 增量索引**：新增 / 修改 / 删除经 Chroma `upsert / delete` 自动同步（停用即下架、删除忽略不存在 id），无需每次全量 rebuild；MySQL 仍是数据源，索引失败仅返回 `indexed:false`，可随时 POST `/index` 重建。

## Java 侧内部接口（阶段三新增）

| 方法 | 路径（context-path=/api） | 请求体 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/ai/internal/order/list` | `{"userId":2095531860995977217}` | 最近 5 笔订单（状态文本映射） |
| POST | `/api/ai/internal/order/detail` | `{"userId":..., "orderNo":"AGS..."}` | 订单详情 + 商品快照 + 物流字段（强制归属校验） |
| POST | `/api/ai/internal/after-sales/list` | `{"userId":...}` | 售后记录（类型 / 状态 / 原因文本映射） |

> 鉴权：请求头 `X-Internal-Key: <服务间密钥>`，取值来自 `ai-service/.env` 的 `JAVA_INTERNAL_KEY`，须与根 `.env` 的 `AI_INTERNAL_KEY` 一致。代码里已无默认值，未配置即一律 401。
> 路径说明：Spring MVC 拦截器 `addPathPatterns` 匹配的是 servlet path（不含 context-path），故注册为 `/ai/internal/**`；JWT 白名单用 `requestURI`（含 context-path），故写 `/api/ai/internal`。

## 目录结构

```
ai-service/
├── app/
│   ├── main.py              # FastAPI 入口（/health、/v1/chat 透传 userId、挂载知识库路由）
│   ├── config.py            # .env 配置（pydantic-settings + load_dotenv 导出环境变量）
│   ├── db.py                # SQLAlchemy 引擎/会话（复用 agsell 主库）
│   ├── prompts.py           # 系统提示词模板（规划 / 作答 / 商品问答 / 闲聊 / 转人工 / 拒答话术）
│   ├── schemas/chat.py      # Pydantic 请求/响应模型（userId 可选）
│   ├── models/faq.py        # ai_faq ORM
│   ├── repositories/faq_repo.py  # FAQ 建表 + CRUD
│   ├── rag/
│   │   ├── vectorstore.py   # Chroma 封装（rebuild 全量重建 / query 余弦相似度）
│   │   └── retriever.py     # 双路召回检索器（向量 + 关键词，去重排序）
│   ├── tools/java_api.py    # httpx 回调 Java 三个内部接口 + 紧凑 JSON 格式化
│   ├── agent/
│   │   ├── state.py         # AgentState（messages + observations/rec_items/product_context/...）
│   │   ├── intent.py        # classify_full（降级路径用：LLM JSON + 订单号正则 + 关键词兜底表）
│   │   ├── tool_defs.py     # 6 个能力的 function schema（只描述"能调什么"）
│   │   ├── executors.py     # 工具执行器 + Observation：已实测生效的闸门全在这里（唯一实现）
│   │   ├── nodes.py         # intent（规则前置）/ agent（bind_tools 有界编排）/ generate / smalltalk / human_fallback
│   │   └── graph.py         # LangGraph 图（build_graph(llm, retriever, java_api_module, product_store, personalizer)；默认 MemorySaver checkpointer）
│   └── api/admin_kb.py      # 知识库管理接口（stats/index/faqs/products，X-Internal-Key 强制且缺配置即 503）
├── scripts/
│   ├── seed_data.py         # 36 条种子 FAQ（基于 agsell 真实业务规则）
│   └── init_faq.py          # 建表 + 种子写入 + 重建索引（--force/--index）
├── tests/                   # pytest 201 个用例（智能体编排 + 工具闸门 + 降级/拒答 + 召回 + 知识库鉴权 + 口碑模板，stub 不依赖网络/LLM）
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
| pytest | 201/201 通过（智能体工具编排 + 执行器闸门 + 降级出口 + 知识库管理接口鉴权 + 口碑句子模板，stub 不依赖网络/LLM）；2026-10-18 实测 |
| Java 内部接口鉴权 | 无 Key → 40101「内部接口鉴权失败」；带 Key → 正常返回；他人订单 detail → 40400 |
| 端到端对话（阶段三，2026-09-18） | ①"我的订单现在是什么状态？"→ 真实 5 笔订单状态汇总；②"订单 AGS2097951729641160704 到哪了？"→ 待发货 + 引导；③"我申请过退款吗？"→ 4 笔已同意 + 1 笔已拒绝明细；④"你好"→ 闲聊回复；⑤未登录查订单/退款 → 引导登录话术；⑥"客服电话是多少？"→ 400-000-0000 |
| 阶段四联调（2026-09-18） | Java AiChatController + 小程序聊天页全链路 10 用例通过：游客 FAQ 4 问、JWT 登录态订单/售后/物流 3 问、多轮上下文 2 问、空消息边界 1 问（详见 doc 设计文档 §14 联调报告）；uniapp `build:h5` 通过 |
| 阶段五加固（2026-09-18） | ① 会话记忆持久化 SqliteSaver：**跨重启实测**（"我叫小明"→ 重启 →"我是谁"答"你是小明呀"）+ SQLite 落盘 28 条链；② Agnes 限流退避 max_retries=2 + 降级话术（FailingLLM stub 验证不白屏）；③ pytest **19/19**；④ Dockerfile + docker-compose（python:3.14-slim + redis-stack）；⑤ 安全复查：`/v1/chat` 新增 X-Internal-Key 鉴权（无 Key 401/带 Key 200），密钥不入 git，越权/隐私/Prompt 注入逐项通过 |
| 阶段七加固（2026-10-10） | ① 意图规则通道（硬规则前置 + 关键词扩充）103 题准确率 **73.8%→96.1%**：smalltalk 50%→100%、human 64%→100%、recommend 60%→100%、logistics 80%→100%、after_sales 73%→100%，人工/寒暄类**零 LLM 调用**、单条≈0ms（剩余 4 条强歧义由 LLM few-shot 覆盖）；② `trim_messages` 历史窗口截断（system 常驻/保留最近/当前问题不丢）；③ Chroma 增量索引 upsert 幂等、delete 忽略不存在 id（fake embedder + 临时目录实测）；④ pytest **50/50**。注：LLM 通道 few-shot 增益需配置 `AGNES_API_KEY` 后跑 `scripts/eval_intent.py --mode llm` 复测。**该 96.1% 后被证伪**：那是调参集上的过拟合读数，同一套规则在 hold-out 只有 **37.6%**（见 `doc/量化实验报告.md` 实验二），本行只作阶段留痕 |

## 阶段规划

| 阶段 | 内容 | 状态 |
| -- | ---------------------------------------- | ----- |
| 一  | 项目骨架 + Agnes AI 连通                       | ✅ 已完成 |
| 二  | FAQ 知识库（MySQL + Chroma + bge + 双路召回）     | ✅ 已完成 |
| 三  | 业务工具（httpx 回调 Java 查订单 / 物流 / 售后 + 意图路由） | ✅ 已完成 |
| 四  | Java 接入（AiChatController）+ 小程序聊天页        | ✅ 已完成 |
| 五  | 加固（SqliteSaver 持久化 / 重试降级 / Docker / 安全复查） | ✅ 已完成 |
| 六  | SSE 流式对话 + 智能导购（偏好抽取 → 商品搜索 → 推荐话术） | ✅ 已完成 |
| 七  | 意图硬规则前置 + few-shot / 历史窗口截断 / FAQ 增量索引（低成本提准降延迟） | ✅ 已完成 |
| 八  | 智能体工具化：6 个 function schema + 有界 ReAct 编排（bind_tools / 去重 / 反思重试 / 能力协商降级）+ 可点击商品卡与商详上下文 | ✅ 已完成 |

## 启动方式

```bash
# 方式一：本地直跑（开发）
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000

# 方式二：Docker 一键起（需 Docker；内置 redis-stack 可选）
docker compose up -d --build
```

依赖配置见 `.env.example`（复制为 `.env` 填写 AGNES_API_KEY 等）。

> 安全提醒：`.env` 已加入 `.gitignore`，请勿提交；Agnes 免费层可能用数据训练，演示勿传真实手机号 / 地址等隐私；服务间密钥已取消代码默认值（`JAVA_INTERNAL_KEY` 未配置即 401），部署时务必用随机值。
