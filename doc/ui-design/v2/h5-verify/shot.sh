#!/usr/bin/env bash
# H5 页面截图核对脚本（品质电商风 v5.0 视觉验收）
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
OUT="D:\\Code\\Practical Project\\agsell\\doc\\ui-design\\v2\\h5-verify"
PROFILE_BASE="C:/Users/31237/AppData/Local/Temp/edge-shot-profile"
SEQ=0

# App.vue 里 H5 宽屏会渲染成 375px 手机画布（并把根字号钉到 16px 让 rpx=0.5px），
# 视口需 >520px 才触发；dsf=2 输出 750px 宽，与设计稿 .phone 等比 1:1。
# 每次截图用独立的 user-data-dir，避免 Edge 复用同一个 headless 实例导致所有截图内容相同。
shot() {
  local url="$1" name="$2"
  SEQ=$((SEQ + 1))
  rm -f "$OUT/$name.png"
  "$EDGE" --headless=new --disable-gpu --hide-scrollbars --no-first-run \
    --user-data-dir="$PROFILE_BASE-$SEQ" --force-device-scale-factor=2 \
    --window-size=700,1600 --virtual-time-budget=18000 \
    --screenshot="$OUT\\$name.png" "$url" 2>&1 | grep -E "bytes written|ERROR:.*screenshot" | tail -1
}

BASE="http://localhost:8081/#"

shot "$BASE/pages/index/index" home
shot "$BASE/pages/category/category" category
shot "$BASE/pages/product/product?id=1" product
shot "$BASE/pages/mine/mine" mine
shot "$BASE/pages/seckill/list" seckill-list
shot "$BASE/pages/trace/detail?batchNo=B202609110001" trace
shot "$BASE/pages/cart/cart" cart
shot "$BASE/pages/ai-chat/chat" ai-chat
shot "$BASE/pages/search/search" search
