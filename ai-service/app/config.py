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

    # 导购个性化重排：final = 语义分 + α·类目偏好 + β·销量归一 + γ·好评率
    # 登录用户才叠加偏好项；游客 β/γ 仍生效（热销兜底语义）
    REC_PREF_ALPHA: float = 0.25
    REC_SALES_BETA: float = 0.10
    REC_RATE_GAMMA: float = 0.10
    # 偏好信号合并权重（订单 : 好评）
    REC_ORDER_WEIGHT: float = 0.6
    REC_REVIEW_WEIGHT: float = 0.4

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
