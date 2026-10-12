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
# 个别品类 Pexels 只有这么几张真图（肉眼逐张核过，如竹笋全部 4 个 SKU 共用 3 张生鲜笋）。
# 硬限流不会把它们匀出去，只会把它们挤掉、换成同一级分类里别的食材，反而更错。
PINNED_REUSE: dict[int, int] = {34027514: 4, 36182943: 4, 20880098: 4}


def reuse_cap(pid: int, base: int) -> int:
    return max(base, PINNED_REUSE.get(pid, 0))



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


def batch_order(path: Path):
    """批次号要按数字比：按字符串排序 9h 会排在 10i 之后，新一轮补采会被旧批次静默覆盖。"""
    tail = path.stem.removeprefix("photos_batch_")
    digits = tail[: len(tail) - len(tail.lstrip("0123456789"))]
    return (int(digits or 0), tail)


def main() -> int:
    dry = "--dry-run" in sys.argv
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    manifest = json.loads((HERE / "manifest.full.json").read_text(encoding="utf-8"))
    products = {str(p["id"]): p for p in manifest["products"]}

    merged: dict[str, list[int]] = {}
    for f in sorted((Path(x) for x in glob.glob(str(OUT / "photos_batch_*.json"))), key=batch_order):
        data = json.loads(f.read_text(encoding="utf-8"))
        for k, v in data.items():
            ids = [int(x) for x in (v or [])]
            # 后加载的批次就是更新一轮的采集结果，非空即覆盖。
            # 曾按"列表更长者优先"合并，会让诚实地只交回 2 张已核验图的补采批次
            # 被上一轮 3 张错配图压掉，等于白采。缺的第 3 张交给同类池兜底。
            if ids:
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

    # 图片池兜底已删除：曾用"同二级/同一级分类的图"补满 3 张，肉眼核对 30 个兜底商品后
    # 约 8 成配错（树莓配草莓、冬瓜配番茄、腐竹配红烧豆腐、挂面配辣椒晾晒场）。
    # 宁可商品只有 1-2 张真图，也不要第 3 张是别的食材——答辩时点开详情就露馅。
    use_count: dict[int, int] = {}
    final: dict[str, list[int]] = {}
    gaps = []
    thin = []
    for pid, p in products.items():
        keep = []
        for i in raw_valid[pid]:
            if i not in keep and len(keep) < PER_PRODUCT and use_count.get(i, 0) < reuse_cap(i, MAX_REUSE):
                use_count[i] = use_count.get(i, 0) + 1
                keep.append(i)
        final[pid] = keep
        if len(keep) < PER_PRODUCT:
            thin.append((pid, p["name"], len(keep)))
        if len(keep) < 2:
            gaps.append((pid, p["name"], p["kw"], len(keep)))

    reused = sorted(((c, i) for i, c in use_count.items() if c > 1), reverse=True)[:10]
    print(f"[INFO] 被 2 个以上商品共用的图片：{reused}")
    print(f"[INFO] 图片齐全({PER_PRODUCT} 张)的商品 {sum(1 for v in final.values() if len(v) == PER_PRODUCT)} / {len(products)}")
    print(f"[INFO] 图片不足 {PER_PRODUCT} 张的商品 {len(thin)}")
    print(f"[INFO] 图片少于 2 张、需要补采的商品 {len(gaps)}")
    for pid, name, kw, n in gaps:
        print(f"   {pid} {name} kw={kw} 现有 {n} 张")

    if dry:
        return 0
    (HERE / "photos.full.json").write_text(
        json.dumps(final, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (OUT / "photo_thin.json").write_text(
        json.dumps([{"id": t[0], "name": t[1], "have": t[2]} for t in thin], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (OUT / "photo_gaps.json").write_text(
        json.dumps([{"id": g[0], "name": g[1], "kw": g[2], "have": g[3]} for g in gaps], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"[OK] 写入 {HERE / 'photos.full.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
