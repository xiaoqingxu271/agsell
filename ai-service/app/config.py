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
    # 偏好信号合并权重（订单 : 好评 : 浏览）。
    # 订单是已验证的真实选择，权重最高；浏览是弱意向信号，但带时间衰减，
    # 因此"刚看过几件腊味"能在一次下拉刷新内改变推荐排序。
    REC_ORDER_WEIGHT: float = 0.5
    REC_REVIEW_WEIGHT: float = 0.3
    REC_BROWSE_WEIGHT: float = 0.2
    # 浏览行为回看窗口与指数衰减半衰期（天）：半衰期 3 天=3 天前的浏览只剩一半权重
    REC_BROWSE_DAYS: int = 7
    REC_BROWSE_HALF_LIFE: int = 3

    # 运行时
    LOG_LEVEL: str = "INFO"

    # 阶段五：会话记忆后端
    #   sqlite = SqliteSaver 持久化到本地文件（默认，跨进程/重启恢复，零外部依赖）
    #   memory  = MemorySaver 进程内（重启丢失，仅调试）
    #   redis   = RedisSaver（需 Redis 8+ 内置 RediSearch；本地 Windows Redis 5 不支持时自动降级 sqlite）
    MEMORY_BACKEND: str = "sqlite"
    REDIS_URL: str = "redis://127.0.0.1:6379/0"
    SQLITE_PATH: str = "data/checkpoints.db"

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
