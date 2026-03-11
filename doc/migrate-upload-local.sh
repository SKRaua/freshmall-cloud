#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="${HOME}/freshmall/upload"

mkdir -p "${TARGET_DIR}/image" "${TARGET_DIR}/avatar"

copy_if_exists() {
  local src="$1"
  if [[ -d "${src}" ]]; then
    rsync -a "${src}/" "${TARGET_DIR}/"
    echo "migrated: ${src} -> ${TARGET_DIR}"
  else
    echo "skip(not found): ${src}"
  fi
}

copy_if_exists "${ROOT_DIR}/freshmall-thing/upload"
copy_if_exists "${ROOT_DIR}/freshmall-user/upload"
copy_if_exists "${ROOT_DIR}/freshmall-order/upload"
copy_if_exists "${ROOT_DIR}/freshmall-gateway/upload"

echo "done. unified upload dir: ${TARGET_DIR}"
