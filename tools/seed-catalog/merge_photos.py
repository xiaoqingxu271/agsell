#!/usr/bin/env python3
"""合并各批次采集到的 Pexels 图片 ID，并逐个用 CDN 校验可用性，产出 photos.full.json。

用法：
  python tools/seed-catalog/merge_photos.py            # 校验 + 写 photos.full.json + 打印缺口
  python tools/seed-catalog/merge_photos.py --dry-run  # 只校验、不落盘

校验口径：images.pexels.com/photos/{id}/pexels-photo-{id}.jpeg 必须 200、Content-Type 为 image/*，
且按 800x800 裁剪参数可取回不小于 400px 的图。
"""
import glob
import json
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
UA = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
}
PER_PRODUCT = 3
MAX_REUSE = 2  # 同一张图最多允许出现在几个商品里


def cdn_url(pid: int) -> str:
    return f"https://images.pexels.com/photos/{pid}/pexels-photo-{pid}.jpeg?auto=compress&cs=tinysrgb&w=800&h=800&fit=crop"


def check(pid: int):
    """Pexels CDN 偶发 30s 超时，单次失败就判定图片不可用会让好图被误丢进兜底池，故重试三次。"""
    last = ""
    for attempt, delay in enumerate((0, 3, 8)):
        if delay:
            time.sleep(delay)
        try:
            req = urllib.request.Request(cdn_url(pid), headers=UA)
            with urllib.request.urlopen(req, timeout=30) as r:
                body = r.read()
                ctype = r.headers.get("Content-Type", "")
                ok = r.status == 200 and ctype.startswith("image/") and len(body) > 8000
                if ok:
                    return pid, True, f"{ctype} {len(body)}B"
                last = f"{r.status} {ctype} {len(body)}B"
        except Exception as e:  # noqa: BLE001
            last = f"{type(e).__name__}: {str(e)[:60]}"
            if isinstance(e, urllib.error.HTTPError) and e.code in (403, 404):
                break  # 明确的死链不必重试
    return pid, False, last


def main() -> int:
    dry = "--dry-run" in sys.argv
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    manifest = json.loads((HERE / "manifest.full.json").read_text(encoding="utf-8"))
    products = {str(p["id"]): p for p in manifest["products"]}

    merged: dict[str, list[int]] = {}
    for f in sorted(glob.glob(str(OUT / "photos_batch_*.json"))):
        data = json.loads(Path(f).read_text(encoding="utf-8"))
        for k, v in data.items():
            ids = [int(x) for x in (v or [])]
            if ids or not merged.get(k):
                if len(ids) >= len(merged.get(k, [])):
                    merged[k] = ids
        print(f"[LOAD] {Path(f).name}: {len(data)} 条")

    unknown = [k for k in merged if k not in products]
    if unknown:
        print(f"[WARN] 批次文件里有未登记的商品 ID：{unknown[:10]}")

    # 只校验出现过的 ID，避免重复请求
    all_ids = sorted({i for ids in merged.values() for i in ids})
    print(f"[INFO] 待校验图片 ID {len(all_ids)} 个")
    with ThreadPoolExecutor(max_workers=12) as pool:
        ok = {pid: (good, why) for pid, good, why in pool.map(check, all_ids)}
    bad = [i for i in all_ids if not ok[i][0]]
    for i in bad[:15]:
        print(f"  [BAD] {i}: {ok[i][1]}")
    print(f"[INFO] 校验失败 {len(bad)} / {len(all_ids)}")

    # 去掉坏 ID + 同商品去重
    raw_valid: dict[str, list[int]] = {}
    for pid in products:
        ids = []
        for i in merged.get(pid, []):
            if ok.get(i, (False,))[0] and i not in ids:
                ids.append(i)
        raw_valid[pid] = ids

    # 图片池兜底：同二级分类 -> 同一级分类（同类食材总比瞎配可靠）
    parent_of = {c["id"]: c["parent_id"] for c in manifest["categories"]}

    def add_pool(pool: dict, key, i: int):
        if i not in pool.setdefault(key, []):
            pool[key].append(i)

    pools: dict[int, list[int]] = {}
    l1_pools: dict[int, list[int]] = {}
    for pid, p in products.items():
        for i in raw_valid[pid]:
            add_pool(pools, p["category_id"], i)
            add_pool(l1_pools, parent_of[p["category_id"]], i)

    # 跨商品限流 + 分类池兜底
    use_count: dict[int, int] = {}
    final: dict[str, list[int]] = {}
    fallback_used = 0
    fallbacks: dict[str, dict] = {}
    gaps = []
    for pid, p in products.items():
        keep = []
        for i in raw_valid[pid]:
            if i not in keep and len(keep) < PER_PRODUCT and use_count.get(i, 0) < MAX_REUSE:
                use_count[i] = use_count.get(i, 0) + 1
                keep.append(i)
        own = len(keep)
        for src, cap in ((pools.get(p["category_id"], []), MAX_REUSE + 1),
                         (l1_pools.get(parent_of[p["category_id"]], []), MAX_REUSE)):
            for i in src:
                if len(keep) >= PER_PRODUCT:
                    break
                if i not in keep and use_count.get(i, 0) < cap:
                    use_count[i] = use_count.get(i, 0) + 1
                    keep.append(i)
        if len(keep) > own:
            fallback_used += 1
            fallbacks[pid] = {
                "name": p["name"],
                "own": raw_valid[pid],
                "added": [i for i in keep if i not in raw_valid[pid]],
            }
        final[pid] = keep
        if len(keep) < PER_PRODUCT:
            gaps.append((pid, p["name"], p["kw"], len(keep)))

    reused = sorted(((c, i) for i, c in use_count.items() if c > 1), reverse=True)[:10]
    print(f"[INFO] 被 2 个以上商品共用的图片：{reused}")
    print(f"[INFO] 图片齐全(3 张)的商品 {sum(1 for v in final.values() if len(v) == 3)} / {len(products)}")
    print(f"[INFO] 用到同类兜底图的商品 {fallback_used}")
    print(f"[INFO] 有缺口的商品 {len(gaps)}")
    for pid, name, kw, n in gaps:
        print(f"   {pid} {name} kw={kw} 现有 {n} 张")

    if dry:
        return 0
    (HERE / "photos.full.json").write_text(
        json.dumps(final, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (OUT / "photo_fallback.json").write_text(
        json.dumps(fallbacks, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (OUT / "photo_gaps.json").write_text(
        json.dumps([{"id": g[0], "name": g[1], "kw": g[2], "have": g[3]} for g in gaps], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"[OK] 写入 {HERE / 'photos.full.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
