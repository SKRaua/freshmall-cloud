#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE=${COMPOSE_FILE:-doc/docker-compose.yml}
AUTO_HEAL=${AUTO_HEAL:-true}
ALERT_WEBHOOK=${ALERT_WEBHOOK:-}

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker 未安装" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "[ERROR] compose 文件不存在: $COMPOSE_FILE" >&2
  exit 1
fi

unhealthy_services=()

while read -r service; do
  [[ -z "$service" ]] && continue
  cid=$(docker compose -f "$COMPOSE_FILE" ps -q "$service" || true)
  if [[ -z "$cid" ]]; then
    unhealthy_services+=("$service(container-missing)")
    continue
  fi

  status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo "unknown")
  echo "[INFO] $service => $status"

  if [[ "$status" != "healthy" && "$status" != "running" ]]; then
    unhealthy_services+=("$service($status)")
    if [[ "$AUTO_HEAL" == "true" ]]; then
      echo "[WARN] 尝试自动重启: $service"
      docker compose -f "$COMPOSE_FILE" restart "$service" >/dev/null
    fi
  fi
done < <(docker compose -f "$COMPOSE_FILE" ps --services)

if [[ ${#unhealthy_services[@]} -gt 0 ]]; then
  msg="freshmall compose 巡检异常: ${unhealthy_services[*]}"
  echo "[ERROR] $msg"

  if [[ -n "$ALERT_WEBHOOK" ]]; then
    curl -sS -X POST "$ALERT_WEBHOOK" \
      -H 'Content-Type: application/json' \
      -d "{\"text\":\"$msg\"}" >/dev/null || true
  fi
  exit 2
fi

echo "[OK] 所有服务健康"
