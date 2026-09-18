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
    JAVA_INTERNAL_KEY: str = "agsell-ai-internal-2026"
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


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
