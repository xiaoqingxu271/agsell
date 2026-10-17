"""实验存档的溯源字段（五个 eval_*.py 共用，唯一实现）

为什么需要它：量化实验报告上一版与存档对不上，根因是**产物不知道自己是在什么状态下跑出来的**——
知识库从 36 条扩到 280 条、关键词规则被扩充过，而报告正文还留着旧数字。现在每个 JSON 都自带：

- measured_at：测量时刻；
- git_commit：HEAD 短哈希；
- worktree_dirty + dirty_files：⚠ 关键项。commit 相同但工作区有未提交改动时，
  "commit 6a7cf8f" 这句话会误导——本轮改动（导购闸门、意图合并）正是这种情况。
  引用数据前先看这两个字段，能避免拿"未提交代码跑出的数"去声称可复现。
"""
import subprocess
from datetime import datetime


def _git(*args: str) -> str:
    repo = __import__("pathlib").Path(__file__).resolve().parents[2]
    try:
        out = subprocess.run(["git", "-C", str(repo), *args],
                             capture_output=True, text=True, timeout=15)
        return out.stdout.strip()
    except Exception:
        return ""


def provenance(extra: dict | None = None) -> dict:
    """返回可直接并入实验产物的溯源字典。"""
    dirty = _git("status", "--porcelain")
    fields = {
        "measured_at": datetime.now().isoformat(timespec="seconds"),
        "git_commit": _git("rev-parse", "--short", "HEAD") or None,
        "worktree_dirty": bool(dirty),
        "dirty_files": len(dirty.splitlines()) if dirty else 0,
    }
    if fields["worktree_dirty"]:
        fields["provenance_warning"] = (
            "本次测量包含未提交的工作区改动，git_commit 不足以唯一确定代码状态；"
            "正式引用前请先提交或注明改动内容（见 doc/项目评估与整改清单.md 的轮次记录）"
        )
    fields.update(extra or {})
    return fields
