"""测试前置：app/config.py 的 JAVA_INTERNAL_KEY 已取消默认值（生产必须由环境注入），
此处提供固定 Key，避免 settings 模块级实例化读到空值导致鉴权相关测试全部 401。"""
import os

os.environ.setdefault("JAVA_INTERNAL_KEY", "test-internal-key")
