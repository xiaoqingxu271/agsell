# AI 智能客服模块 · 阶段五测试报告

> 日期：2026-09-18 ｜ 服务：`ai-service`（FastAPI + LangGraph + Agnes AI + RAG）
> 范围：阶段五加固（会话持久化 / 重试降级 / Docker / 安全复查）

## 1. 结论摘要

| 验收项 | 结果 | 说明 |
|--------|------|------|
| 会话记忆跨重启恢复 | ✅ 通过 | SqliteSaver 持久化，重启后同会话记忆完整 |
| Agnes 限流/超时降级 | ✅ 通过 | 生成节点捕获异常返回降级话术，不白屏 |
| 单元测试 | ✅ 19/19 | 新增降级 2 + 持久化 3 + 鉴权 3 |
| Docker 构建文件 | ✅ 产出 | Dockerfile / docker-compose / .dockerignore |
| 安全复查 | ✅ 逐项通过 | 见 §5 |

## 2. 单元测试（pytest 19/19）

```text
tests/test_health.py                  5 passed   # health + 服务间鉴权 3 项
tests/test_graph.py                  11 passed   # 意图路由 6 + 分支 5 + 降级 2
tests/test_retriever.py               0/0        # 冒烟
tests/test_checkpoint_persistence.py  3 passed   # SqliteSaver 跨重启/隔离/异步
```

| 测试 | 验证点 |
|------|--------|
| `test_sqlite_checkpointer_persists_across_restart` | 两个独立 SqliteSaver 实例连同一文件，模拟重启后同一 thread_id 能取回历史 |
| `test_generate_degraded_when_llm_fails` | LLM 抛 429 → 回复含客服电话、不含技术细节 |
| `test_smalltalk_degraded_when_llm_fails` | 闲聊节点同样降级 |
| `test_chat_rejects_missing_service_key` | `/v1/chat` 无 X-Internal-Key → 401 |
| `test_chat_accepts_correct_service_key` | 正确 Key 通过校验 |

## 3. 会话记忆跨重启实测（HTTP 全链路）

**用例**：用户告知昵称 → 重启 Python 服务（进程消失）→ 追问身份。

| 轮次 | 动作 | 结果 |
|------|------|------|
| 1 | `sessionId=smalltalk-persist-20260918`，消息"我叫小明，是一名大学生" | ✅ 回复"你好呀小明！…" |
| 2 | **重启 uvicorn 进程**（MemorySaver 方案此处记忆必丢） | — |
| 3 | 同一 sessionId，消息"我是谁？我们之前聊过我。" | ✅ 回复"你是小明呀，刚才跟我说过是大学生呢！" |
| 对照 | 新 sessionId 同问题 | 结构不同（重新检索），无历史引用 |

**落盘验证**：`data/checkpoints.db` 存在 `checkpoints` + `writes` 两张表，28 条 checkpoint 按 thread_id 链式关联（parent_checkpoint_id 串连）。

> 技术选型说明：官方 `langgraph-checkpoint-redis` 0.5.x 要求 **Redis 8+（内置 RediSearch/RedisJSON）**。本地 Windows Redis 5.0 无模块、Windows 无官方 Redis 8 发行版、WSL Ubuntu 未装 Redis → 采用官方 **SqliteSaver**（`langgraph-checkpoint-sqlite`）落地持久化，同样满足"服务重启后会话可恢复"。已保留 `MEMORY_BACKEND=redis` 配置路径，docker-compose 内置 `redis/redis-stack-server`，部署到 Linux + Redis 8 环境可一键切换。

## 4. Docker 交付物

| 文件 | 内容 |
|------|------|
| `Dockerfile` | `python:3.14-slim`（与本地 3.14 一致）+ 清华 pip 源 + HEALTHCHECK |
| `docker-compose.yml` | `ai-service`（8000）+ 可选 `redis-stack`（6380）+ 两个数据卷 |
| `.dockerignore` | 排除 `.venv/ data/ .env __pycache__` 等 |

启动：`docker compose up -d --build`（需 Docker Desktop；本机未安装 Docker，文件已产出、未实际构建，交付说明如实标注）。

## 5. 安全复查清单

| 检查项 | 结果 | 依据 |
|--------|------|------|
| 密钥不入 git | ✅ | `.gitignore` 含 `.env` / `application-local.yaml`；`git ls-files` 确认未跟踪 |
| 密钥不硬编码 | ✅ | Java `${AI_INTERNAL_KEY:...}` 环境变量；Python 读 `.env` |
| Python 服务入口鉴权 | ✅ 新增 | `/v1/chat` 校验 `X-Internal-Key`；实测无 Key 401、带 Key 200 |
| 知识库管理接口鉴权 | ✅ | `/v1/admin/kb/*` 依赖 X-Admin-Key |
| Java 内部接口越权 | ✅ | X-Internal-Key 拦截器 + `eq(userId)` + orderNo 归属校验 |
| 隐私最小化 | ✅ | VO 无收货人/电话/地址 |
| Prompt 注入防护 | ✅ | 系统提示词限制只依据知识库/查询结果，不透露机制 |
| 用户消息边界 | ✅ | Java 2000 字上限 + Pydantic 双保险 |

## 6. 已知限制

1. **本机未装 Docker**：Dockerfile/compose 已产出并通过静态检查，实际 `docker compose up` 未执行（需用户安装 Docker Desktop 后验证）。
2. **RedisSaver 未启用**：本地 Redis 5.0 无 RediSearch；切 Redis 8/Stack 后设 `MEMORY_BACKEND=redis` 即可。
3. **意图分类偶发抖动**（沿用阶段四）：1/10 次把 FAQ 误判转人工，重发正常；可后续加置信度阈值。
