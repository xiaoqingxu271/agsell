"""bge-reranker 交叉编码器重排（ONNX 量化版，本地 CPU 推理，不引 torch）

论文实验八的落点：三路融合召回的 Hit@1 提升最稳的一招是重排。
- 模型：BAAI/bge-reranker-base 的量化 ONNX（Xenova 导出，266MB），data/reranker/ 下；
- 运行时：onnxruntime（fastembed 的既有依赖）+ tokenizers，零新增依赖、零 LLM 额度；
- 打分：逐对 (query, doc) 过编码器取 logit，sigmoid 后 0~1；
- 与闸门的关系：重排只调整"已过闸门条目"的先后，绝不放行新条目——反幻觉语义不变。
"""
import logging
from functools import lru_cache
from pathlib import Path

import numpy as np

from app.config import settings

logger = logging.getLogger("agsell.ai.reranker")


class CrossEncoderReranker:
    def __init__(self, model_dir: str | None = None, max_length: int = 256):
        self._dir = Path(model_dir or settings.RERANK_MODEL_PATH)
        self._max_length = max_length
        self._session = None
        self._tokenizer = None

    def _ensure_loaded(self) -> None:
        if self._session is not None:
            return
        import onnxruntime
        from tokenizers import Tokenizer

        model_file = self._dir / "onnx" / "model_quantized.onnx"
        tokenizer_file = self._dir / "tokenizer.json"
        if not model_file.exists() or not tokenizer_file.exists():
            raise FileNotFoundError(
                f"reranker 模型不完整：{model_file.exists()}=onnx, {tokenizer_file.exists()}=tokenizer")
        self._session = onnxruntime.InferenceSession(
            str(model_file), providers=["CPUExecutionProvider"])
        self._tokenizer = Tokenizer.from_file(str(tokenizer_file))
        self._tokenizer.enable_truncation(max_length=self._max_length)
        self._tokenizer.no_padding()
        logger.info("reranker 已加载：%s", model_file)

    def score(self, query: str, documents: list[str], batch_size: int = 8) -> list[float]:
        """逐对打分，返回与 documents 等长的 0~1 相关度列表（倒序排列前先别裁剪）"""
        self._ensure_loaded()
        pairs = [(query, d) for d in documents]
        enc = self._tokenizer.encode_batch(pairs)
        scores: list[float] = []
        input_names = {i.name for i in self._session.get_inputs()}
        for start in range(0, len(enc), batch_size):
            batch = enc[start:start + batch_size]
            maxlen = max(len(e.ids) for e in batch)
            input_ids = np.zeros((len(batch), maxlen), dtype=np.int64)
            attention = np.zeros((len(batch), maxlen), dtype=np.int64)
            for i, e in enumerate(batch):
                n = len(e.ids)
                input_ids[i, :n] = e.ids
                attention[i, :n] = e.attention_mask
            feed = {"input_ids": input_ids, "attention_mask": attention}
            if "token_type_ids" in input_names:
                feed["token_type_ids"] = np.zeros_like(input_ids)
            logits = self._session.run(None, feed)[0].reshape(-1)
            probs = 1.0 / (1.0 + np.exp(-logits))
            scores.extend(probs.tolist())
        return scores

    def rerank(self, query: str, entries: list[dict], text_of, top_k: int) -> list[dict]:
        """按交叉编码分重排并截断：entries 原序即融合分序（重排分同分时保持融合序）。"""
        if not entries:
            return entries
        scores = self.score(query, [text_of(e) for e in entries])
        for e, s in zip(entries, scores):
            e["_rerank"] = round(float(s), 4)
        entries.sort(key=lambda x: (-x["_rerank"],))
        out = entries[:top_k]
        for e in out:
            e.pop("_rerank", None)
        return out


@lru_cache
def get_reranker() -> CrossEncoderReranker:
    return CrossEncoderReranker()
