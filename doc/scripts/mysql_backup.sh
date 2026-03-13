#!/usr/bin/env bash
set -euo pipefail

MYSQL_CONTAINER=${MYSQL_CONTAINER:-freshmall-mysql}
MYSQL_USER=${MYSQL_USER:-root}
MYSQL_PASSWORD=${MYSQL_PASSWORD:-root}
MYSQL_DATABASE=${MYSQL_DATABASE:-freshmall_db}
BACKUP_DIR=${BACKUP_DIR:-./doc/backups/mysql}
RETENTION_DAYS=${RETENTION_DAYS:-7}

mkdir -p "$BACKUP_DIR"

timestamp=$(date +%Y%m%d_%H%M%S)
backup_file="$BACKUP_DIR/${MYSQL_DATABASE}_${timestamp}.sql.gz"

echo "[INFO] 开始备份 MySQL: $MYSQL_CONTAINER/$MYSQL_DATABASE"
docker exec "$MYSQL_CONTAINER" sh -c "mysqldump -u${MYSQL_USER} -p${MYSQL_PASSWORD} --single-transaction --quick --routines --events ${MYSQL_DATABASE}" \
  | gzip > "$backup_file"

echo "[OK] 备份完成: $backup_file"

find "$BACKUP_DIR" -type f -name "${MYSQL_DATABASE}_*.sql.gz" -mtime +"$RETENTION_DAYS" -delete

echo "[OK] 已清理超过 ${RETENTION_DAYS} 天的历史备份"
