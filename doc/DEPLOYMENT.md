# Freshmall 部署与运维说明

## 1. Docker Compose 一键部署

### 前置条件
- Docker 24+
- Docker Compose v2
- Maven 3.8+

### 启动
```bash
cd /home/skraua/work/freshmall-cloud
bash doc/sh
```

### 单独执行
```bash
# 1) 合并初始化 SQL（已生成可跳过）
cat doc/dump-freshmall_db-202602221232.sql doc/sql/20260311_add_b_cart.sql > doc/sql/000_merged_init.sql

# 2) 打包后端
mvn -q -pl freshmall-gateway,freshmall-user,freshmall-thing,freshmall-order -am package -DskipTests

# 3) 启动
docker compose -f doc/docker-compose.yml up -d --build
```

## 1.1 单独部署中间件（MySQL + Redis）

```bash
cd /home/skraua/work/freshmall-cloud
bash doc/scripts/deploy_middleware.sh
# 或使用快捷脚本
bash doc/sh-middleware
```

说明：
- 使用文件：`doc/docker-compose.middleware.yml`
- 仅部署 MySQL 与 Redis，不启动业务服务
- 可重复执行，结果保持一致（幂等）：
  - 复用固定容器名与命名卷
  - 已存在的数据卷不会被重新初始化

## 2. 数据库初始化合并
- 已合并文件：`doc/sql/000_merged_init.sql`
- Compose 已挂载至 MySQL 初始化目录：`/docker-entrypoint-initdb.d/000_merged_init.sql`

> 仅在首次初始化数据目录时执行；若已有旧数据卷，请先清理数据卷再初始化。

## 3. 健康巡检与自愈

### Docker 健康巡检
```bash
cd /home/skraua/work/freshmall-cloud
./doc/scripts/compose_health_audit.sh
```

支持变量：
- `AUTO_HEAL=true|false`（默认 true）
- `ALERT_WEBHOOK=https://your-webhook`（异常时推送）

### 建议定时任务（每 2 分钟）
```bash
*/2 * * * * cd /home/skraua/work/freshmall-cloud && ALERT_WEBHOOK="https://xxx" ./doc/scripts/compose_health_audit.sh >> /var/log/freshmall-health.log 2>&1
```

## 4. MySQL 备份策略

### 手动备份
```bash
cd /home/skraua/work/freshmall-cloud
./doc/scripts/mysql_backup.sh
```

### 建议定时任务（每天凌晨 2 点）
```bash
0 2 * * * cd /home/skraua/work/freshmall-cloud && ./doc/scripts/mysql_backup.sh >> /var/log/freshmall-backup.log 2>&1
```

默认保留 7 天，可通过 `RETENTION_DAYS` 覆盖。

## 5. 日志策略
- Compose 已配置 `json-file` 滚动：`max-size=20m`, `max-file=5`
- 生产建议：
  - 收集容器日志到 Loki/ELK
  - 应用日志落盘并由 logrotate 管理
  - 关键错误日志转告警（Webhook/钉钉/企业微信）

## 6. Kubernetes 部署

清单位于：`doc/k8s/freshmall-all.yaml`

```bash
kubectl apply -f doc/k8s/freshmall-all.yaml
kubectl -n freshmall get pods,svc
```

说明：
- 镜像默认 `imagePullPolicy: Never`，用于本地镜像场景
- 若使用 kind/minikube，需要先把镜像导入集群节点
- liveness/readiness 使用 TCP 探针

## 7. Ansible 一键部署

```bash
cd /home/skraua/work/freshmall-cloud
ansible-playbook -i doc/ansible/inventory.ini doc/ansible/site.yml
```

## 8. containerd 巡检

脚本：`doc/scripts/containerd_health_audit.sh`

```bash
cd /home/skraua/work/freshmall-cloud
./doc/scripts/containerd_health_audit.sh
```

- 有 `crictl` 时：检测 Exited 容器并可告警
- 仅 `ctr` 时：输出任务状态，建议结合 systemd/cron 做自动恢复

## 9. 故障自愈与告警建议

### 自愈
- Compose：`restart: unless-stopped` + 健康巡检脚本自动 `restart`
- Kubernetes：`livenessProbe` 失败自动重启

### 告警
- 低成本：巡检脚本 + `ALERT_WEBHOOK`
- 标准化：Prometheus + Alertmanager + Grafana
  - 监控项：容器重启次数、健康检查失败、MySQL 连接失败、网关 5xx
