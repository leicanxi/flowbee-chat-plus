#!/usr/bin/env bash
#
# 把构建产物发布到 Cloudflare R2（FlowBee App 的自更新通道）
#
# 需要的环境变量：
#   AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY   R2 API 令牌（workflow 从 Secrets 注入）
#   R2_ENDPOINT        形如 https://<accountid>.r2.cloudflarestorage.com
#   R2_BUCKET          存储桶名（pb-files）
#   R2_PUBLIC_BASE     绑在桶上的自定义域名，形如 https://fbc-dl.flowbee.top
#   BUILD_NUMBER       CI 构建序号，单调递增
#   VERSION_NAME       App 上显示的版本号
#   APK_FILE           要发布的 APK（arm64-v8a）
#   COMMIT_SHA         本次提交（可选，默认取当前 HEAD）
#
# 产出两个文件：
#   app/update.json                      固定地址，App 靠它判断有没有新版本
#   app/flowbee-<版本>-arm64-v8a.apk     每次构建一个新文件（旧链接永久有效，方便回滚）
#
set -euo pipefail

: "${R2_ENDPOINT:?缺少 R2_ENDPOINT}" "${R2_BUCKET:?缺少 R2_BUCKET}" "${R2_PUBLIC_BASE:?缺少 R2_PUBLIC_BASE}"
: "${BUILD_NUMBER:?缺少 BUILD_NUMBER}" "${VERSION_NAME:?缺少 VERSION_NAME}" "${APK_FILE:?缺少 APK_FILE}"
COMMIT_SHA="${COMMIT_SHA:-$(git rev-parse HEAD)}"

UPDATE_KEY="app/update.json"
UPDATE_URL="$R2_PUBLIC_BASE/$UPDATE_KEY"
APK_NAME="flowbee-${VERSION_NAME}-arm64-v8a.apk"
APK_KEY="app/$APK_NAME"
APK_URL="$R2_PUBLIC_BASE/$APK_KEY"
KEEP_APK=5
R2_REGION="${R2_REGION:-auto}"

aws_s3() {
  aws --endpoint-url "$R2_ENDPOINT" --region "$R2_REGION" "$@"
}

# ——— 读取上一次的发布信息（拿上次的提交号，用来算"这次改了什么"）———
echo "==> 读取上一次的更新说明：$UPDATE_URL"
PREV_JSON="$(curl -fsS --max-time 20 "$UPDATE_URL" 2>/dev/null || echo '{}')"
PREV_COMMIT=""
PREV_PUBLISHED_AT=""
PREV_META="$(PREV_JSON="$PREV_JSON" python3 - <<'PY' 2>/dev/null || true
import json, os
try:
    data = json.loads(os.environ.get("PREV_JSON") or "{}")
except Exception:
    data = {}
# 用 | 分隔而不是空格：字段为空时也能被 read 正确切开
print(data.get("commit") or "", data.get("publishedAt") or "", sep="|")
PY
)"
IFS='|' read -r PREV_COMMIT PREV_PUBLISHED_AT <<<"${PREV_META:-|}"
echo "    上次提交：${PREV_COMMIT:-（无）}"
echo "    上次发布时间：${PREV_PUBLISHED_AT:-（无）}"

# ——— 更新内容：默认从提交记录生成；release-notes.md 有改动时优先用它的内容 ———
CHANGELOG=""
if [ -f release-notes.md ]; then
  NOTES_TS="$(git log -1 --format=%ct -- release-notes.md 2>/dev/null || echo 0)"
  PREV_TS=0
  if [ -n "$PREV_PUBLISHED_AT" ]; then
    PREV_TS="$(date -d "$PREV_PUBLISHED_AT" +%s 2>/dev/null || echo 0)"
  fi
  # release-notes.md 里的 `#` 开头行是注释（模板说明），不进更新说明
  NOTES_BODY="$(grep -v -E '^[[:space:]]*#' release-notes.md || true)"
  NOTES_BODY="$(printf '%s\n' "$NOTES_BODY" | sed -e 's/[[:space:]]*$//' | grep -v -E '^$' || true)"
  if [ "${NOTES_TS:-0}" -gt "$PREV_TS" ] && [ -n "${NOTES_BODY//[[:space:]]/}" ]; then
    CHANGELOG="$NOTES_BODY"
    echo "==> 使用 release-notes.md 的内容作为更新说明"
  elif [ -n "${NOTES_BODY//[[:space:]]/}" ]; then
    echo "==> release-notes.md 上次发版后没改过，忽略它"
  else
    echo "==> release-notes.md 里还没有实际内容，忽略它"
  fi
fi

if [ -z "${CHANGELOG//[[:space:]]/}" ]; then
  echo "==> 从提交记录生成更新说明"
  if [ -n "$PREV_COMMIT" ] && git cat-file -e "${PREV_COMMIT}^{commit}" 2>/dev/null; then
    CHANGELOG="$(git log --no-merges --pretty=format:'- %s' "${PREV_COMMIT}..HEAD")"
  else
    CHANGELOG="$(git log --no-merges -n 20 --pretty=format:'- %s' HEAD)"
  fi
  # 去掉"改版本号"这类对用户没意义的提交
  CHANGELOG="$(printf '%s\n' "$CHANGELOG" | grep -v -E '^- (chore: )?(bump|release|版本)' || true)"
  CHANGELOG="$(printf '%s\n' "$CHANGELOG" | head -n 30)"
fi

if [ -z "${CHANGELOG//[[:space:]]/}" ]; then
  CHANGELOG="- 常规更新与问题修复"
fi

# ⚠️ --checksum-algorithm CRC32 不能省：
# AWS CLI 2.23.0 起默认改发 CRC64-NVME 校验和，而 R2 不支持它，上传会直接报 InternalError。
# 这是 R2 官方文档给出的规避方式（CLI 版本 2.22.35 / 1.36.40 无此问题）。
# 上传时必须显式指定，删除/列举不受影响。

# ——— 先传安装包，后传说明：保证用户不会看到一个安装包还不存在的版本 ———
echo "==> 上传安装包：$APK_KEY"
aws_s3 s3 cp "$APK_FILE" "s3://$R2_BUCKET/$APK_KEY" \
  --checksum-algorithm CRC32 \
  --content-type "application/vnd.android.package-archive" \
  --cache-control "public, max-age=31536000, immutable"

APK_BYTES="$(stat -c%s "$APK_FILE" 2>/dev/null || stat -f%z "$APK_FILE")"
APK_SIZE="$(awk -v b="$APK_BYTES" 'BEGIN { printf "%.2f MB", b/1048576 }')"
PUBLISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

BODY_FILE="$(mktemp)"
VERSION_NAME="$VERSION_NAME" \
BUILD_NUMBER="$BUILD_NUMBER" \
PUBLISHED_AT="$PUBLISHED_AT" \
CHANGELOG="$CHANGELOG" \
COMMIT_SHA="$COMMIT_SHA" \
APK_NAME="$APK_NAME" \
APK_URL="$APK_URL" \
APK_SIZE="$APK_SIZE" \
python3 - "$BODY_FILE" <<'PY'
import json, os, sys

payload = {
    "version": os.environ["VERSION_NAME"],
    "buildNumber": int(os.environ["BUILD_NUMBER"]),
    "publishedAt": os.environ["PUBLISHED_AT"],
    "changelog": os.environ["CHANGELOG"],
    "commit": os.environ["COMMIT_SHA"],
    "downloads": [
        {
            "name": os.environ["APK_NAME"],
            "url": os.environ["APK_URL"],
            "size": os.environ["APK_SIZE"],
        }
    ],
}

with open(sys.argv[1], "w", encoding="utf-8") as handle:
    json.dump(payload, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
PY

echo "==> 上传更新说明：$UPDATE_KEY"
aws_s3 s3 cp "$BODY_FILE" "s3://$R2_BUCKET/$UPDATE_KEY" \
  --checksum-algorithm CRC32 \
  --content-type "application/json; charset=utf-8" \
  --cache-control "public, max-age=60"
# 用 command rm 绕过 shell 里可能存在的 rm 包装（回收站包装对 /tmp 路径会失败）
command rm -f "$BODY_FILE"

# ——— 只保留最近 $KEEP_APK 个安装包，旧的删掉，避免存储无限堆积 ———
echo "==> 清理旧安装包（保留最近 $KEEP_APK 个）"
LIST="$(aws_s3 s3 ls "s3://$R2_BUCKET/app/" | grep -E '\.apk$' | sort || true)"
TO_DELETE="$(printf '%s\n' "$LIST" | grep -v '^[[:space:]]*$' | head -n "-$KEEP_APK" || true)"
if [ -n "${TO_DELETE//[[:space:]]/}" ]; then
  while IFS= read -r line; do
    [ -z "${line//[[:space:]]/}" ] && continue
    key="$(awk '{print $4}' <<<"$line")"
    echo "    删除 $key"
    aws_s3 s3 rm "s3://$R2_BUCKET/$key"
  done <<<"$TO_DELETE"
else
  echo "    无需清理"
fi

echo "==> 发布完成：$VERSION_NAME（build $BUILD_NUMBER）"
echo "    安装包：$APK_URL"
echo "    更新说明：$UPDATE_URL"
