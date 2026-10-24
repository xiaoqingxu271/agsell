"""测试前置：app/config.py 的 JAVA_INTERNAL_KEY 已取消默认值（生产必须由环境注入），
此处提供固定 Key，避免 settings 模块级实例化读到空值导致鉴权相关测试全部 401。"""
import os

os.environ.setdefault("JAVA_INTERNAL_KEY", "test-internal-key")

# 重排默认在生产打开（实验八数据支撑），但单测必须确定性走融合序：
# 真模型加载 ~1.7s 且依赖 data/reranker/ 文件存在——单测不验证模型，只验证检索逻辑。
# 单测里要测重排逻辑本身时，monkeypatch settings.RERANK_ENABLED=True 并替换 get_reranker。
from app.config import settings as _settings

_settings.RERANK_ENABLED = False

# 引用日志/运营统计的落库路径同样与真实数据隔离：pytest 的流式测试会走完整的
# /v1/chat/stream 链路（真实写 chat_log），不重定向会把测试轮次写进生产统计。
import os as _os
import tempfile as _tempfile

_settings.KB_HIT_LOG_PATH = _os.path.join(_tempfile.gettempdir(), "agsell-test-kb-hits.db")
