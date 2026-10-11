#!/usr/bin/env python3
"""把 out/photos 里的商品主图按一级分类拼成核对图，供肉眼检查图文是否对应。

用法：
  python tools/seed-catalog/qa_sheet.py                      # 全部分类，每类一张拼图
  python tools/seed-catalog/qa_sheet.py --cells 20           # 每张拼图最多 20 格
产出：out/qa/<一级分类id>-<名称>.png 与 out/qa_icons.png（52 个分类图标一览）
"""
import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
QA = OUT / "qa"


def font(size: int) -> ImageFont.ImageFont:
    try:
        return ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", size)
    except OSError:
        return ImageFont.load_default()


def cell(path: Path, size: int, label: str) -> Image.Image:
    im = Image.open(path).convert("RGB")
    im.thumbnail((size, size), Image.LANCZOS)
    box = Image.new("RGB", (size, size + 34), (250, 250, 250))
    box.paste(im, ((size - im.width) // 2, 0))
    d = ImageDraw.Draw(box)
    d.text((6, size + 4), label[:22], fill=(30, 30, 30), font=font(18))
    return box


def sheet(tiles: list, cols: int, size: int, title: str) -> Image.Image:
    rows = (len(tiles) + cols - 1) // cols
    pad, head = 8, 46
    img = Image.new("RGB", (cols * (size + pad) + pad, head + rows * (size + pad + 34)), (255, 255, 255))
    d = ImageDraw.Draw(img)
    d.text((pad + 4, 12), title, fill=(20, 20, 20), font=font(24))
    for n, t in enumerate(tiles):
        r, c = divmod(n, cols)
        img.paste(t, (pad + c * (size + pad), head + r * (size + pad + 34)))
    return img


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--cells", type=int, default=24)
    ap.add_argument("--size", type=int, default=220)
    ap.add_argument("--icons", action="store_true")
    args = ap.parse_args()

    manifest = json.loads((HERE / "manifest.full.json").read_text(encoding="utf-8"))
    cats = {c["id"]: c for c in manifest["categories"]}
    l1_of = {c["id"]: c["parent_id"] if c["parent_id"] else c["id"] for c in manifest["categories"]}
    QA.mkdir(parents=True, exist_ok=True)

    if args.icons:
        tiles = [cell(OUT / "icons" / f"{c['id']}.png", 120, c["name"]) for c in manifest["categories"]
                 if (OUT / "icons" / f"{c['id']}.png").exists()]
        sheet(tiles, 8, 120, "分类图标一览").save(OUT / "qa_icons.png")
        print(f"[OK] 图标一览 {len(tiles)} 个 -> {OUT / 'qa_icons.png'}")
        return 0

    groups: dict[int, list] = {}
    for p in manifest["products"]:
        main_img = OUT / "photos" / f"{p['id']}-0.jpg"
        if not main_img.exists():
            continue
        name = p["name"].split()[0]
        groups.setdefault(l1_of[p["category_id"]], []).append((main_img, f"{name}|{cats[p['category_id']]['name']}"))

    for cid, items in groups.items():
        tiles = [cell(f, args.size, lab) for f, lab in items[: args.cells]]
        out = QA / f"{cid}-{cats[cid]['name']}.png"
        sheet(tiles, 6, args.size, f"{cats[cid]['name']} 商品主图（{len(items)} 个商品，显示前 {len(tiles)}）").save(out)
        print(f"[OK] {out.name}: {len(tiles)}/{len(items)}")
    missing = [p["id"] for p in manifest["products"] if not (OUT / "photos" / f"{p['id']}-0.jpg").exists()]
    if missing:
        print(f"[WARN] 缺主图的商品 {len(missing)}: {missing[:20]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
