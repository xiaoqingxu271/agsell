"""Agnes AI 连通性测试：发起一次真实对话，验证 Key / Base URL / 模型名。

用法：.venv\\Scripts\\python scripts\\test_agnes.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from langchain_openai import ChatOpenAI

from app.config import settings


def main() -> None:
    if not settings.AGNES_API_KEY:
        print("[FAIL] AGNES_API_KEY 未配置，请检查 .env")
        return

    llm = ChatOpenAI(
        model=settings.AGNES_MODEL,
        base_url=settings.AGNES_BASE_URL,
        api_key=settings.AGNES_API_KEY,
        temperature=settings.AGNES_TEMPERATURE,
        timeout=30,
    )
    print(f"[INFO] base_url = {settings.AGNES_BASE_URL}")
    print(f"[INFO] model    = {settings.AGNES_MODEL}")

    reply = llm.invoke("你好，请用一句话介绍你自己。")
    print(f"[OK] 回复：{reply.content}")


if __name__ == "__main__":
    main()
