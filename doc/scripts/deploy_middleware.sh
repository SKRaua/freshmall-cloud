#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd)
COMPOSE_FILE="$ROOT_DIR/doc/docker-compose.middleware.yml"
MERGED_SQL="$ROOT_DIR/doc/sql/000_merged_init.sql"
BASE_SQL="$ROOT_DIR/doc/dump-freshmall_db-202602221232.sql"
CART_SQL="$ROOT_DIR/doc/sql/20260311_add_b_cart.sql"

if [[ ! -f "$MERGED_SQL" ]]; then
  if [[ ! -f "$BASE_SQL" || ! -f "$CART_SQL" ]]; then
    echo "[ERR] 缺少初始化 SQL 文件，无法生成 $MERGED_SQL" >&2
    exit 1
  fi
  cat "$BASE_SQL" "$CART_SQL" > "$MERGED_SQL"
  echo "[INFO] 已生成初始化 SQL: $MERGED_SQL"
fi

echo "[1/2] 启动中间件（MySQL + Redis）..."
docker compose -f "$COMPOSE_FILE" up -d

echo "[2/2] 当前状态："
docker compose -f "$COMPOSE_FILE" ps

echo "[OK] 中间件已就绪。重复执行该脚本会保持同一配置状态。"
