#!/usr/bin/env python3
"""把 out/ 素材通过后端 /api/file/upload 接口上传到对象存储（阿里云 OSS），并缓存返回的 URL。

用法：
  python tools/seed-catalog/upload_oss.py                 # 管理员账号默认 admin，密码取环境变量 ADMIN_PASSWORD 或 admin123
  python tools/seed-catalog/upload_oss.py --base-url http://127.0.0.1:8080/api --workers 4

幂等：按文件 sha1 缓存到 .state/upload_cache.json，重复执行不会重传同一张图。
产出：out/urls.json  { "icon:1001": "https://...", "photo:20001:0": "https://..." }
"""
import argparse
import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
STATE = HERE / ".state"

UA = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
}


def login(base: str, username: str, password: str) -> str:
    r = requests.post(
        f"{base}/admin/login",
        json={"username": username, "password": password},
        headers=UA,
        timeout=20,
    )
    body = r.json()
    if body.get("code") != 0:
        raise SystemExit(f"[ERR] 管理端登录失败：{body}")
    return body["data"]["token"]


def upload_one(base: str, token: str, asset: dict, local_root: Path) -> str:
    path = local_root / asset["file"]
    ctype = "image/png" if path.suffix.lower() == ".png" else "image/jpeg"
    for attempt in range(3):
        try:
            with path.open("rb") as fh:
                r = requests.post(
                    f"{base}/file/upload",
                    params={"prefix": asset["prefix"], "fileType": "IMAGE"},
                    headers={"Authorization": f"Bearer {token}", **UA},
                    files={"file": (path.name, fh, ctype)},
                    timeout=60,
                )
            body = r.json()
            if body.get("code") == 0 and body.get("data"):
                return body["data"]
            raise RuntimeError(f"code={body.get('code')} message={body.get('message')}")
        except Exception as e:  # noqa: BLE001 - 网络抖动/限流重试
            if attempt == 2:
                raise RuntimeError(f"{asset['key']} 上传失败：{e}") from e
            time.sleep(1.5 * (attempt + 1))
    raise AssertionError


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default="http://127.0.0.1:8080/api")
    ap.add_argument("--username", default="admin")
    ap.add_argument("--password", default=os.environ.get("ADMIN_PASSWORD", "admin123"))
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    assets = json.loads((OUT / "assets.json").read_text(encoding="utf-8"))
    STATE.mkdir(exist_ok=True)
    cache_file = STATE / "upload_cache.json"
    cache = json.loads(cache_file.read_text(encoding="utf-8")) if cache_file.exists() else {}

    todo = [a for a in assets if a["sha1"] not in cache]
    print(f"[INFO] 素材 {len(assets)} 项，已缓存 {len(assets) - len(todo)} 项，待上传 {len(todo)} 项")
    if args.dry_run or not todo:
        urls = {a["key"]: cache.get(a["sha1"]) for a in assets}
        (OUT / "urls.json").write_text(json.dumps(urls, ensure_ascii=False, indent=2), encoding="utf-8")
        return 0

    token = login(args.base_url, args.username, args.password)
    print("[OK] 管理端登录成功，开始上传")

    done = 0
    failed = []
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(upload_one, args.base_url, token, a, HERE): a for a in todo}
        for fut in as_completed(futures):
            a = futures[fut]
            try:
                cache[a["sha1"]] = fut.result()
                done += 1
                if done % 20 == 0:
                    print(f"  ... 已上传 {done}/{len(todo)}")
                    cache_file.write_text(json.dumps(cache, indent=2), encoding="utf-8")
            except Exception as e:  # noqa: BLE001
                failed.append(str(e))
    cache_file.write_text(json.dumps(cache, indent=2), encoding="utf-8")

    urls = {a["key"]: cache.get(a["sha1"]) for a in assets}
    missing = [k for k, v in urls.items() if not v]
    (OUT / "urls.json").write_text(json.dumps(urls, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[OK] 本次上传 {done} 项，累计可用 URL {len(urls) - len(missing)} 项")
    if missing:
        print(f"[ERR] {len(missing)} 项缺少 URL：{missing[:10]}")
        for msg in failed[:10]:
            print("   -", msg)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
