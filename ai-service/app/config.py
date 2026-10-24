"""应用配置：从 .env 读取密钥与模型参数（pydantic-settings）"""
import os
from functools import lru_cache

from dotenv import load_dotenv
from pydantic_settings import BaseSettings, SettingsConfigDict

# 把 .env 导出到进程环境变量（HF_ENDPOINT 等需被第三方库读取）
load_dotenv()


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )

    # Agnes AI 免费大模型
    AGNES_API_KEY: str = ""
    AGNES_BASE_URL: str = "https://apihub.agnes-ai.com/v1"
    AGNES_MODEL: str = "agnes-2.5-flash"
    AGNES_TEMPERATURE: float = 0.3

    # Java 主系统服务间鉴权（阶段三使用）
    # 无默认值：未注入 JAVA_INTERNAL_KEY 时校验恒失败（fail-closed），不会静默沿用公开密钥
    JAVA_INTERNAL_KEY: str = ""
    JAVA_BASE_URL: str = "http://127.0.0.1:8080/api"

    # MySQL（复用 agsell 主库，FAQ 数据源）
    DATABASE_URL: str = "mysql+pymysql://root:root@127.0.0.1:3306/agsell?charset=utf8mb4"

    # FAQ 知识库（RAG）
    PLATFORM_NAME: str = "农产品销售系统"
    SERVICE_PHONE: str = "400-000-0000"
    # fastembed 内置支持的中文模型；如要换 bge-m3 需改用 sentence-transformers 路径
    EMBEDDING_MODEL: str = "BAAI/bge-small-zh-v1.5"
    VECTOR_DB_PATH: str = "data/"
    KB_TOP_K: int = 5
    # 检索得分阈值（反幻觉闸门）：低于阈值的向量召回不注入上下文，检索为空则走确定性拒答。
    # 依据量化实验：域内 top-1 相似度均值 0.710、域外仅 0.329，0.5 居中可分。0 = 不启用。
    KB_SCORE_THRESHOLD: float = 0.5
    # 语义确认带上界：向量分落在 [阈值, 0.6) 的「商品域条目」必须有词面佐证（关键词/全文命中）
    # 才能进入结果，否则剔除——2026-10-24 复测实证：域内 top-1 最低 0.557 与域外最高 0.596 区间
    # 重叠，单阈值不可分；而泄漏样本命中的是商品域条目（category_knowledge 的「食用油」类目），
    # 其卖点文本与任何购物语境都相似度偏高；平台规则条目在带内实测 10/10 正确，无需加验。
    KB_SCORE_CONFIRM_BAND: float = 0.6
    # 视为"商品域条目"的知识库分类（逗号分隔）：这些条目受确认带加验约束
# （trace_knowledge 是溯源切块条目，同为具体商品事实，语义分布与商品域一致）
    KB_PRODUCT_KNOWLEDGE_CATEGORIES: str = "product_knowledge,category_knowledge,trace_knowledge"

    # 关键词通道低区分度通用词（命中不加权、也不豁免阈值）：
    # 实验五发现"推荐/什么时候"类通用词曾让"推荐电影"等域外问法穿透闸门
    KB_KEYWORD_STOPWORDS: str = "推荐,什么时候,多少,怎么,如何,哪里,什么,为什么,有哪些"

    # 商品向量召回（智能导购，collection="product"）
    PRODUCT_TOP_K: int = 8

    # 语义搜索（搜索框接入向量召回）：一次召回较多候选交 Java 与全文/在售过滤后合并
    REC_SEARCH_TOP_K: int = 50
    # 语义命中相似度闸门：低于此分的候选不作为语义结果（域外词退回全文检索）
    # 依据量化实验：域内 top-1 均值 ~0.71、域外 ~0.33，取 0.35 略偏召回
    REC_SEARCH_MIN_SCORE: float = 0.35

    # 对话导购分支的独立闸门（不要用上面那个！）：
    # REC_SEARCH_MIN_SCORE 是为**搜索框**定的——空结果能优雅退回 MySQL 全文检索，所以阈值可以偏松。
    # 导购分支没有那样的退路：闸门一松就硬把农产品塞给域外请求。
    # 量化实验五实测：0.35 下 10 条域外推荐请求放行 6 条（60%），端上桌的是
    # "手机壳→带壳毛豆""口红→丹东红颜草莓""跑步鞋→金华火腿"；阈值-风险曲线在 0.47 处
    # 域外泄漏率降到 0%、域内召回仍保持 90%。故对话侧取 0.47。
    REC_MIN_SCORE: float = 0.47

    # 对话里导购卡片的条数上限。E2E 实测：语义召回按 top_k 整批回传，聊天气泡下面一次冒出
    # 8 张卡（还混进夏威夷果），用户不会往下翻——卡片少而准比"看起来给得多"更有用。
    REC_CARD_LIMIT: int = 4

    # 混合排序（feed / 语义搜索 / 对话导购共用一套口径，实现见 rag/personalize.score_products）：
    #   final = 语义分 + α·类目偏好 + β·销量归一 + γ·好评率 + δ·新鲜度
    # 无查询场景（首页 feed）语义分恒为 0，其余四项与另外两条链路完全同权重——
    # 同一件商品换入口不该拿到不同分。游客不叠加偏好项，其余项仍生效。
    REC_PREF_ALPHA: float = 0.25
    REC_SALES_BETA: float = 0.10
    REC_RATE_GAMMA: float = 0.10
    REC_FRESH_DELTA: float = 0.15
    # 好评率 Bayesian 收缩的"虚拟先验评价条数"：实际口碑样本极稀（全库 202 件在售商品仅 7 件
    # 有已分析评价、人均 1.7 条），直接用原始好评率会让"1 条差评"和"零评价"都被当成差商品。
    # 取 2 条：单条评价只把分拉离全站均值约 2/3 档，既有区分度又不会被一条评价定生死。
    REC_RATE_PRIOR_COUNT: float = 2.0
    # 偏好信号合并权重（订单 : 收藏 : 好评 : 加购 : 浏览 : 搜索词）。
    # 订单是已验证的真实选择，权重最高；收藏是显式强意向（比浏览可信、比下单稠密）；
    # 加购是临门一脚的意向但可能被比价后放弃；搜索词是当下最"新鲜"的需求表达，
    # 但关键词到品类的映射是模糊的（LIKE 商品名），权重最低。
    REC_ORDER_WEIGHT: float = 0.5
    REC_FAVORITE_WEIGHT: float = 0.35
    REC_REVIEW_WEIGHT: float = 0.3
    REC_CART_WEIGHT: float = 0.3
    REC_BROWSE_WEIGHT: float = 0.2
    REC_SEARCH_WEIGHT: float = 0.15
    # 搜索词信号回看窗口（天）：搜索意图易过时，只取近几天的词
    REC_SEARCH_SIGNAL_DAYS: int = 7
    # 浏览行为回看窗口与指数衰减半衰期（天）：半衰期 3 天=3 天前的浏览只剩一半权重
    REC_BROWSE_DAYS: int = 7
    REC_BROWSE_HALF_LIFE: int = 3

    # 智能体（工具编排）：有界 ReAct 循环
    # 实测（Agnes agnes-2.5-flash）：bind_tools 可用，且一条消息里的多个诉求会在一轮内
    # **并行**发出多个 tool_calls——这是"多意图只能走一支"的解法。
    # 但同样实测到：把工具结果喂回去后模型可能**不收敛**（再发一遍同样的调用），
    # 因此步数必须硬上限，且按 (工具,参数) 去重防止原地打转；最终回复由独立的
    # generate 节点基于工具观察作答，不依赖模型自觉停止调用。
    AGENT_MAX_STEPS: int = 2        # 每轮对话最多让模型规划几次（每次都可并行多工具）
    AGENT_MAX_TOOL_CALLS: int = 4   # 单轮最多执行的工具调用总数（含并行），防额度被一条消息打满

    # 运行时
    LOG_LEVEL: str = "INFO"

    # 阶段五：会话记忆后端
    #   sqlite = SqliteSaver 持久化到本地文件（默认，跨进程/重启恢复，零外部依赖）
    #   memory  = MemorySaver 进程内（重启丢失，仅调试）
    #   redis   = RedisSaver（需 Redis 8+ 内置 RediSearch；本地 Windows Redis 5 不支持时自动降级 sqlite）
    MEMORY_BACKEND: str = "sqlite"
    REDIS_URL: str = "redis://127.0.0.1:6379/0"
    SQLITE_PATH: str = "data/checkpoints.db"
    # 知识库引用日志（运营视图数据源，见 kb_hit_log.py）；与 checkpoints 同目录、独立库文件
    KB_HIT_LOG_PATH: str = "data/kb_hits.db"
    KB_HIT_TOP_DAYS: int = 7
    KB_HIT_TOP_LIMIT: int = 10

    # 知识库重排（实验八）：bge-reranker-base 量化 ONNX，本地 CPU 推理。
    # 重排只调整已过闸门条目的先后，不放行新条目；实验八实测 platform_faq
    # Hit@1 90.3%→97.2%、Hit@3 95.8%→100%，重排延迟 P50 219ms（20 候选），
    # 对 9.6s 的 FAQ 首 token 路径占比 <3%，故默认打开；失败自动回退融合序。
    RERANK_ENABLED: bool = True
    RERANK_MODEL_PATH: str = "data/reranker"
    RERANK_RECALL_DEPTH: int = 20   # 参与重排的召回候选数（检索器先召回这么多再重排截断）
    # 会话 TTL（天）：超过 N 天未活跃的会话在启动与每日维护时从 checkpoints.db 清理。
    # 0 = 关闭清理。依据：state.messages 随会话无限增长（发送侧 3000 字符截断只管提示词），
    # 长期运行 checkpoints.db 膨胀（评估清单 §4 第 5 条的工程债）。
    CHECKPOINT_TTL_DAYS: int = 7
    CHECKPOINT_CLEANUP_INTERVAL_HOURS: int = 24

    # 阶段五：Agnes 调用重试与降级
    LLM_TIMEOUT: float = 30.0   # 单次 LLM 请求超时（秒）
    LLM_MAX_RETRIES: int = 2    # 429/5xx 自动退避重试次数

    # 阶段七：多轮历史窗口截断（发给 LLM 的对话字符预算，system 常驻、保留最近对话）
    # 中文按字符近似计 token；0 = 不截断。用于控制长会话的 token 成本与首 token 延迟。
    HISTORY_MAX_CHARS: int = 3000


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
