# ============================================================
# agsell AI 智能客服服务 Dockerfile（阶段五）
# 基础镜像 python:3.14-slim（本地开发 Python 3.14，与生产一致）
# ============================================================
FROM python:3.14-slim

# 时区 + 常用系统依赖（chromadb/fastembed 无需编译，仅基础工具）
ENV TZ=Asia/Shanghai \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# 先复制依赖清单，利用 Docker 层缓存
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 复制源码
COPY app ./app
COPY scripts ./scripts

# 数据目录（向量库 + SQLite checkpoints）
RUN mkdir -p /app/data

EXPOSE 8000

# 健康检查：FastAPI /health
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
