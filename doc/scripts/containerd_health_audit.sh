#!/usr/bin/env bash
set -euo pipefail

NAMESPACE=${NAMESPACE:-k8s.io}
ALERT_WEBHOOK=${ALERT_WEBHOOK:-}

if command -v crictl >/dev/null 2>&1; then
  echo "[INFO] 使用 crictl 巡检（Kubernetes/containerd）"
  unhealthy=$(crictl ps -a --state Exited -q | wc -l | tr -d ' ')
  if [[ "$unhealthy" != "0" ]]; then
    msg="containerd 巡检发现 Exited 容器数量: $unhealthy"
    echo "[ERROR] $msg"
    [[ -n "$ALERT_WEBHOOK" ]] && curl -sS -X POST "$ALERT_WEBHOOK" -H 'Content-Type: application/json' -d "{\"text\":\"$msg\"}" >/dev/null || true
    exit 2
  fi
  echo "[OK] 未发现 Exited 容器"
  exit 0
fi

if command -v ctr >/dev/null 2>&1; then
  echo "[INFO] 使用 ctr 巡检（纯 containerd）"
  ctr -n "$NAMESPACE" tasks list || true
  echo "[TIP] 建议配合 systemd + cron：当任务不在 RUNNING 时触发重启与告警"
  exit 0
fi

echo "[ERROR] 未找到 crictl/ctr，无法执行 containerd 巡检" >&2
exit 1
