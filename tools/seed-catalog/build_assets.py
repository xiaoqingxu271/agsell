#!/usr/bin/env python3
"""按 manifest 下载并标准化素材：分类图标（Iconify）+ 商品图/轮播图（Pexels CDN）。

用法：
  python tools/seed-catalog/build_assets.py --manifest tools/seed-catalog/manifest.sample.json
  python tools/seed-catalog/build_assets.py --manifest ... --style-preview   # 额外生成多种图标风格对比图

产出（out/ 已在 .gitignore 中忽略）：
  out/photos/{product_id}-{i}.jpg   800x800 商品图
  out/icons/{category_id}.png       160x160 白底分类图标
  out/assets.json                   待上传清单（含 sha1，供上传脚本幂等复用）
"""
import argparse
import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFont

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
UA = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
}
session = requests.Session()
session.headers.update(UA)

ICON_SET_DEFAULT = "fluent-emoji-flat"
ICON_STYLES = ["fluent-emoji-flat", "noto", "twemoji", "openmoji"]


def get(url, *, timeout=30, stream=False):
    """带退避重试：Iconify 在连续请求后会返回 429/连接异常，Pexels CDN 偶发超时。"""
    for attempt, delay in enumerate((0, 2, 6, 14, 30)):
        if delay:
            time.sleep(delay)
        try:
            r = session.get(url, timeout=timeout, stream=stream)
            if r.status_code == 200 or r.status_code in (404, 401, 403):
                return r
        except requests.RequestException as e:
            if attempt == 4:
                print(f"[WARN] 请求失败 {url[:80]}: {e}")
    return None


def sha1(path: Path) -> str:
    h = hashlib.sha1()
    h.update(path.read_bytes())
    return h.hexdigest()


def pexels_url(pid: int, size: int) -> str:
    return (
        f"https://images.pexels.com/photos/{pid}/pexels-photo-{pid}.jpeg"
        f"?auto=compress&cs=tinysrgb&w={size}&h={size}&fit=crop"
    )


def build_photo(pid: int, dest: Path, size: int, errors: list) -> bool:
    if dest.exists() and dest.stat().st_size > 10_000:
        return True
    r = get(pexels_url(pid, size))
    if r is None or r.status_code != 200 or not r.headers.get("content-type", "").startswith("image"):
        errors.append(f"pexels photo {pid}: HTTP {getattr(r, 'status_code', 'n/a')}")
        return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".raw")
    tmp.write_bytes(r.content)
    try:
        im = Image.open(tmp)
        im.load()
        if min(im.size) < 400:
            errors.append(f"pexels photo {pid}: 尺寸过小 {im.size}")
            return False
        im.convert("RGB").save(dest, "JPEG", quality=85, optimize=True)
    except Exception as e:
        errors.append(f"pexels photo {pid}: 解码失败 {e}")
        return False
    finally:
        tmp.unlink(missing_ok=True)
    return True


def icon_svg_url(icon_set: str, name: str, size: int) -> str:
    return f"https://api.iconify.design/{icon_set}/{name}.svg?width={size}&height={size}"


def resolve_icon_name(icon_set: str, name: str) -> str:
    """图标名不存在时用 Iconify 搜索接口取第一个同义词，避免 emoji 集命名差异导致漏图。"""
    r = get(icon_svg_url(icon_set, name, 256))
    if r is not None and r.status_code == 200:
        return name
    sr = get(f"https://api.iconify.design/search?query={name}&limit=4&prefix={icon_set}")
    if sr is not None and sr.status_code == 200:
        hits = sr.json().get("icons") or []
        if hits:
            resolved = hits[0].split(":", 1)[1]
            print(f"[INFO] 图标名回退 {icon_set}:{name} -> {icon_set}:{resolved}")
            return resolved
    return name


def download_icon_svg(icon_set: str, name: str, dest: Path, size: int) -> bool:
    resolved = resolve_icon_name(icon_set, name)
    r = get(icon_svg_url(icon_set, resolved, size * 2))
    if r is None or r.status_code != 200 or b"<svg" not in r.content[:400]:
        print(f"[WARN] 图标下载失败 {icon_set}:{name} ({resolved})")
        return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(r.content)
    return True


def svg_to_png(jobs: list, workdir: Path) -> bool:
    """用 node + sharp 批量栅格化 SVG（Windows 无 cairo，cairosvg 不可用）。"""
    if not jobs:
        return True
    jobs_file = workdir / "icon_jobs.json"
    jobs_file.write_text(json.dumps(jobs), encoding="utf-8")
    res = subprocess.run(
        ["node", str(HERE / "icons.mjs"), str(jobs_file)],
        cwd=str(workdir),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    print((res.stdout or "").strip())
    if res.returncode != 0:
        print(res.stderr[-800:])
        return False
    return True


def pad_to_square(src: Path, dest: Path, size: int, bg=(255, 255, 255)) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    im = Image.open(src).convert("RGBA")
    canvas = Image.new("RGBA", (size, size), bg + (255,))
    scale = (size * 0.92) / max(im.size)
    im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.LANCZOS)
    canvas.alpha_composite(im, ((size - im.width) // 2, (size - im.height) // 2))
    canvas.convert("RGB").save(dest, "PNG", optimize=True)


def icon_cell(path: Path, size: int) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    canvas = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    sc = (size * 0.86) / max(im.size)
    im2 = im.resize((int(im.width * sc), int(im.height * sc)), Image.LANCZOS)
    canvas.alpha_composite(im2, ((size - im2.width) // 2, (size - im2.height) // 2))
    return canvas.convert("RGB")


def style_preview(categories: list, icon_sets: list, size: int) -> Path:
    """生成图标风格对比图（列=风格，行=分类），供定风格时肉眼挑选。"""
    prev_dir = OUT / "preview"
    prev_dir.mkdir(parents=True, exist_ok=True)
    jobs = []
    for c in categories:
        for s in icon_sets:
            svg = prev_dir / f"{c['id']}_{s}.svg"
            if download_icon_svg(s, c["icon"], svg, size) and not (prev_dir / f"{c['id']}_{s}.raw.png").exists():
                jobs.append({"svg": str(svg), "out": str(prev_dir / f"{c['id']}_{s}.raw.png"), "size": size * 2})
    svg_to_png(jobs, prev_dir)

    cols, rows = len(icon_sets), len(categories)
    cell = size + 24
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 20)
    except OSError:
        font = ImageFont.load_default()
    sheet = Image.new("RGB", (cols * cell, rows * cell + 44), (245, 246, 248))
    draw = ImageDraw.Draw(sheet)
    for col, s in enumerate(icon_sets):
        draw.text((col * cell + 12, 12), s, fill=(40, 40, 40), font=font)
    for row, c in enumerate(categories):
        for col, s in enumerate(icon_sets):
            p = prev_dir / f"{c['id']}_{s}.raw.png"
            if p.exists():
                sheet.paste(icon_cell(p, size), (col * cell + 12, 44 + row * cell))
            draw.text((col * cell + 12, 44 + row * cell + size + 6), c["name"], fill=(40, 40, 40), font=font)
    out = OUT / "icon_style_preview.png"
    sheet.save(out)
    print(f"[OK] 图标风格对比图：{out}")
    return out


def assert_photos_synced(manifest: dict, manifest_path: str) -> None:
    """build_assets 只认 manifest.products[].photos，不读 photos.full.json。

    merge_photos.py 改的是 photos.full.json，必须先跑 make_manifest.py 才会落到 manifest，
    否则本轮采集的新图会被静默忽略、下载的还是旧 ID（踩过一次，白跑一整轮素材）。
    """
    pf = HERE / "photos.full.json"
    if not pf.exists():
        return
    merged = {str(k): [int(x) for x in v] for k, v in json.loads(pf.read_text(encoding="utf-8")).items()}
    pids = [str(p["id"]) for p in manifest["products"]]
    if sum(1 for i in pids if i in merged) < len(merged) // 2:
        return  # 小样 manifest 只覆盖部分商品，不参与全量口径校验
    stale = [
        f"{p['id']} {p['name']}: manifest={p['photos']} photos.full={merged[str(p['id'])]}"
        for p in manifest["products"]
        if str(p["id"]) in merged and [int(x) for x in p["photos"]] != merged[str(p["id"])]
    ]
    if stale:
        print(f"[ERR] {len(stale)} 个商品的 manifest 图片 ID 落后于 photos.full.json，先跑 "
              f"python tools/seed-catalog/make_manifest.py 再跑本脚本。前 5 条：", flush=True)
        for s in stale[:5]:
            print("   " + s, flush=True)
        sys.exit(2)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--style-preview", action="store_true")
    args = ap.parse_args()

    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    assert_photos_synced(manifest, args.manifest)
    meta = manifest["meta"]
    icon_set = meta.get("icon_prefix", ICON_SET_DEFAULT)
    icon_size = meta.get("icon_size", 160)
    photo_size = meta.get("photo_size", 800)

    errors: list[str] = []
    assets = []

    if args.style_preview:
        style_preview(manifest["categories"], ICON_STYLES, 128)

    # 1. 分类图标
    svg_jobs = []
    for c in manifest["categories"]:
        svg = OUT / "svg" / f"{c['id']}.svg"
        png = OUT / "icons" / f"{c['id']}.png"
        if not png.exists():
            time.sleep(0.3)
            if not download_icon_svg(icon_set, c["icon"], svg, icon_size):
                errors.append(f"category icon {c['id']} {c['name']}: {icon_set}:{c['icon']}")
                continue
            svg_jobs.append({"svg": str(svg), "out": str(OUT / "svg" / f"{c['id']}.raw.png"), "size": icon_size * 2})
    if svg_to_png(svg_jobs, OUT / "svg"):
        for c in manifest["categories"]:
            raw = OUT / "svg" / f"{c['id']}.raw.png"
            png = OUT / "icons" / f"{c['id']}.png"
            if raw.exists():
                pad_to_square(raw, png, icon_size)
                assets.append(
                    {
                        "key": f"icon:{c['id']}",
                        "kind": "category_icon",
                        "ref_id": c["id"],
                        "file": str(png.relative_to(HERE)),
                        "prefix": "category/icon",
                        "sha1": sha1(png),
                    }
                )

    # 2. 商品图
    for p in manifest["products"]:
        for i, pid in enumerate(p["photos"]):
            dest = OUT / "photos" / f"{p['id']}-{i}.jpg"
            if build_photo(pid, dest, photo_size, errors):
                assets.append(
                    {
                        "key": f"photo:{p['id']}:{i}",
                        "kind": "product_photo",
                        "ref_id": p["id"],
                        "index": i,
                        "pexels_id": pid,
                        "file": str(dest.relative_to(HERE)),
                        "prefix": "product/image",
                        "sha1": sha1(dest),
                    }
                )

    # 3. 轮播图
    for b in manifest.get("banners", []):
        if not b.get("photo"):
            errors.append(f"banner {b['id']} {b.get('title')}: 没有图片 ID")
            continue
        dest = OUT / "banners" / f"{b['id']}.jpg"
        if build_photo(b["photo"], dest, photo_size, errors):
            assets.append(
                {
                    "key": f"banner:{b['id']}",
                    "kind": "banner",
                    "ref_id": b["id"],
                    "file": str(dest.relative_to(HERE)),
                    "prefix": "banner",
                    "sha1": sha1(dest),
                }
            )

    (OUT / "assets.json").write_text(json.dumps(assets, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[OK] 素材清单 {len(assets)} 项 -> {OUT / 'assets.json'}")
    if errors:
        print(f"[WARN] {len(errors)} 项缺失/失败：")
        for e in errors:
            print("   -", e)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
