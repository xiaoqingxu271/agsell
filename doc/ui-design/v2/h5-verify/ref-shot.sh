#!/usr/bin/env bash
# 用与 shot.sh 完全相同的拍摄参数渲染设计稿 8 屏，便于逐屏像素对照
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
OUT="D:\\Code\\Practical Project\\agsell\\doc\\ui-design\\v2\\h5-verify"
SRC="file:///D:/Code/Practical Project/agsell/doc/ui-design/v2/full-flow.html"
PROFILE_BASE="C:/Users/31237/AppData/Local/Temp/edge-ref-profile"
SEQ=0

ref() {
  local n="$1" name="$2"
  SEQ=$((SEQ + 1))
  rm -f "$OUT/$name.png"
  "$EDGE" --headless=new --disable-gpu --hide-scrollbars --no-first-run \
    --user-data-dir="$PROFILE_BASE-$SEQ" --force-device-scale-factor=2 \
    --window-size=700,1600 --virtual-time-budget=12000 \
    --screenshot="$OUT\\$name.png" "$SRC?s=$n" 2>&1 | grep -E "bytes written|ERROR:.*screenshot" | tail -1
}

ref 1 screen-01
ref 2 screen-02
ref 3 screen-03
ref 4 screen-04
ref 5 screen-05
ref 6 screen-06
ref 7 screen-07
ref 8 screen-08
