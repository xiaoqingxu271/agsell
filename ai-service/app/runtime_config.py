"""运行时可调配置（sys_config 表 ai_* 键）：管理端「AI 运营配置」页改，无需重启。

与 config.py（pydantic-settings，进程启动时从环境定死）分层：这里只放运营要调的
少数运行参数，读 sys_config 带 TTL 缓存，PUT 后主动失效、立刻生效。

三条铁律：
- 兜底——表不存在/库断/值脏，一律回落 settings 默认值，调参通道绝不能把对话搞挂；
- 夹取——数值越界按 min/max 夹取，防止手滑把检索条数改成 999；
- 白名单——只认 FIELDS 里登记的键，其他键读写都不碰（sys_config 是共享表）。
"""
import logging
import time

from sqlalchemy import text

from app.config import settings
from app.db import engine

logger = logging.getLogger("agsell.ai.runtime_config")

_TTL_SECONDS = 60.0

# 键 → (settings 属性名, 类型, 中文说明, 最小值, 最大值)
FIELDS: dict[str, tuple[str, type, str, float | None, float | None]] = {
    "ai_kb_top_k": ("KB_TOP_K", int, "知识库检索返回条数", 1, 20),
    "ai_kb_score_threshold": ("KB_SCORE_THRESHOLD", float, "向量闸门阈值（低于该分不进答案，0=关闭）", 0.0, 1.0),
    "ai_rerank_enabled": ("RERANK_ENABLED", bool, "交叉编码重排开关（关=按融合分直出）", None, None),
    "ai_agent_max_steps": ("AGENT_MAX_STEPS", int, "智能体编排步数上限（规划-执行轮数）", 1, 6),
    "ai_agent_max_tool_calls": ("AGENT_MAX_TOOL_CALLS", int, "单轮工具调用次数上限", 1, 12),
}

_cache: dict[str, str] | None = None
_loaded_at = 0.0


def _load(force: bool = False) -> dict[str, str]:
    """读 sys_config 的 ai_* 键（TTL 缓存）。任何失败回落空 dict（= 全默认）。"""
    global _cache, _loaded_at
    now = time.monotonic()
    if not force and _cache is not None and (now - _loaded_at) < _TTL_SECONDS:
        return _cache
    try:
        with engine.connect() as conn:
            rows = conn.execute(
                text("select config_key, config_value from sys_config where config_key like 'ai\\_%' escape '\\'")
            ).all()
        _cache = {k: ("" if v is None else str(v)) for k, v in rows}
        _loaded_at = now
    except Exception:
        # 失败也记时间：有旧缓存时按 TTL 内继续用旧值，库挂期间不会每轮对话都重试打库
        logger.warning("sys_config 读取失败，AI 运行参数沿用缓存/默认值")
        if _cache is None:
            _cache = {}
        _loaded_at = now
    return _cache


def invalidate() -> None:
    """管理端 PUT 后主动失效，下次读取即取新值"""
    global _cache, _loaded_at
    _cache = None
    _loaded_at = 0.0


def effective(key: str):
    """当前生效值：sys_config 覆盖优先，缺/脏回落 settings 默认。"""
    spec = FIELDS.get(key)
    if spec is None:
        raise KeyError(f"未登记的运行参数：{key}")
    attr, typ, _desc, lo, hi = spec
    default = getattr(settings, attr)
    raw = _load().get(key, "")
    if raw == "":
        return default
    try:
        if typ is bool:
            val = raw.strip().lower() in ("1", "true", "yes", "on")
        elif typ is int:
            val = int(float(raw))
        else:
            val = float(raw)
    except (ValueError, TypeError):
        return default
    if lo is not None:
        val = max(lo, val)
    if hi is not None:
        val = min(hi, val)
    return val


def set_values(values: dict[str, str]) -> list[str]:
    """upsert 白名单键并失效缓存，返回实际更新的键。值校验失败抛 ValueError。"""
    updated: list[str] = []
    clean: dict[str, str] = {}
    for key, raw in (values or {}).items():
        if key not in FIELDS:
            continue  # 非白名单键静默忽略，不报错（批量提交里混入旧键不该失败）
        spec = FIELDS[key]
        normalized = _normalize(key, raw)
        clean[key] = normalized
        updated.append(key)
    if not updated:
        return []
    with engine.begin() as conn:
        for key, val in clean.items():
            conn.execute(
                text(
                    "insert into sys_config (config_key, config_value, description) "
                    "values (:k, :v, :d) "
                    "on duplicate key update config_value = :v"
                ),
                {"k": key, "v": val, "d": f"AI 运行参数（{FIELDS[key][2]}）"},
            )
    invalidate()
    return updated


def _normalize(key: str, raw: str) -> str:
    """归一为合法字符串（越界夹取/布尔归一），失败抛 ValueError 由端点转 400"""
    spec = FIELDS[key]
    _attr, typ, desc, lo, hi = spec
    raw = (raw or "").strip()
    if raw == "":
        raise ValueError(f"{desc}：值不能为空（可恢复默认请填默认值 {getattr(settings, spec[0])}）")
    try:
        if typ is bool:
            low = raw.lower()
            if low in ("1", "true", "yes", "on", "开", "开启"):
                return "true"
            if low in ("0", "false", "no", "off", "关", "关闭"):
                return "false"
            raise ValueError
        if typ is int:
            val = int(float(raw))
        else:
            val = float(raw)
    except (ValueError, TypeError) as e:
        raise ValueError(f"{desc}：无法解析为{'布尔' if typ is bool else '数值'}值") from e
    if lo is not None:
        val = max(lo, val)
    if hi is not None:
        val = min(hi, val)
    return str(val)


def snapshot() -> list[dict]:
    """GET 用：每个登记键的当前值/默认值/来源/类型/说明/边界"""
    raw_values = _load()
    items = []
    for key, (attr, typ, desc, lo, hi) in FIELDS.items():
        raw = raw_values.get(key, "")
        default = getattr(settings, attr)
        current = effective(key)
        items.append({
            "key": key,
            "description": desc,
            "type": "bool" if typ is bool else ("int" if typ is int else "float"),
            "value": current,
            "default": default,
            "overridden": raw != "",
            "min": lo,
            "max": hi,
        })
    return items
