# Freshmall K8s 发布与环境变量速查

这份文档用于发布前核对与排障，优先回答 4 件事：

- 文件怎么用
- 变量在哪里改
- 线上怎么发和怎么回滚
- `order` 后续怎么平滑从 outbox 迁移到 MQ

## 1. 清单职责

- `doc/k8s/freshmall-all-middle.yaml`：只包含中间件与基础资源（namespace/pvc/secret/mysql/nacos/redis/rabbitmq）。
- `doc/k8s/freshmall-all.yaml`：全量部署，包含业务服务 Deployment + 环境变量。

## 2. 服务变量一览

### 2.1 freshmall-user

- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`，默认 `127.0.0.1:8848`
- `SPRING_DATASOURCE_URL`，默认本地 MySQL URL
- `SPRING_DATASOURCE_USERNAME`，默认 `root`
- `SPRING_DATASOURCE_PASSWORD`，默认 `root`
- `FILE_UPLOADPATH`，默认 `${user.home}/freshmall/upload`

### 2.2 freshmall-thing

- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`，默认 `127.0.0.1:8848`
- `SPRING_DATASOURCE_URL`，默认本地 MySQL URL
- `SPRING_DATASOURCE_USERNAME`，默认 `root`
- `SPRING_DATASOURCE_PASSWORD`，默认 `root`
- `FILE_UPLOADPATH`，默认 `${user.home}/freshmall/upload`
- `SPRING_REDIS_HOST`，默认 `127.0.0.1`
- `SPRING_REDIS_PORT`，默认 `6379`
- `SPRING_REDIS_PASSWORD`，默认空字符串
- `SPRING_REDIS_DATABASE`，默认 `0`
- `SPRING_CACHE_TYPE`，默认 `redis`

### 2.3 freshmall-order

- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`，默认 `127.0.0.1:8848`
- `SPRING_DATASOURCE_URL`，默认本地 MySQL URL
- `SPRING_DATASOURCE_USERNAME`，默认 `root`
- `SPRING_DATASOURCE_PASSWORD`，默认 `root`
- `ORDER_TIMEOUT_PENDING_PAY_MS`，默认 `1800000`
- `ORDER_TIMEOUT_SCAN_INTERVAL_MS`，默认 `60000`
- `ORDER_EVENT_DISPATCH_INTERVAL_MS`，默认 `1000`

预留 MQ 变量（已接入配置，占位可直接生效）：

- `SPRING_RABBITMQ_HOST`，默认 `127.0.0.1`
- `SPRING_RABBITMQ_PORT`，默认 `5672`
- `SPRING_RABBITMQ_USERNAME`，默认 `guest`
- `SPRING_RABBITMQ_PASSWORD`，默认 `guest`
- `SPRING_RABBITMQ_VIRTUAL_HOST`，默认 `/`

### 2.4 freshmall-gateway

- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`，默认 `127.0.0.1:8848`
- `FILE_UPLOADPATH`，默认 `${user.home}/freshmall/upload`

## 3. Secret 映射

- `mysql-secret.root-password` -> `MYSQL_ROOT_PASSWORD` 和各服务 `SPRING_DATASOURCE_PASSWORD`
- `rabbitmq-secret.username` -> `RABBITMQ_DEFAULT_USER` 与 `SPRING_RABBITMQ_USERNAME`
- `rabbitmq-secret.password` -> `RABBITMQ_DEFAULT_PASS` 与 `SPRING_RABBITMQ_PASSWORD`

提示：生产环境不要使用 `root`、`guest/guest`。

## 4. 变量占位代码位置

- `freshmall-user/src/main/resources/application.yml`
- `freshmall-thing/src/main/resources/application.yml`
- `freshmall-order/src/main/resources/application.yml`
- `freshmall-gateway/src/main/resources/application.yml`

## 5. 发布步骤

### 5.1 先发中间件

```bash
kubectl apply -f doc/k8s/freshmall-all-middle.yaml
kubectl -n freshmall get pods
kubectl -n freshmall get svc
```

### 5.2 再发全量业务

```bash
kubectl apply -f doc/k8s/freshmall-all.yaml
kubectl -n freshmall rollout status deploy/freshmall-user
kubectl -n freshmall rollout status deploy/freshmall-thing
kubectl -n freshmall rollout status deploy/freshmall-order
kubectl -n freshmall rollout status deploy/freshmall-gateway
```

### 5.3 运行态检查

```bash
kubectl -n freshmall get pods -o wide
kubectl -n freshmall describe pod <pod-name>
kubectl -n freshmall logs deploy/freshmall-order --tail=200
```

## 6. 回滚

### 6.1 快速回滚单服务

```bash
kubectl -n freshmall rollout undo deploy/freshmall-order
```

### 6.2 回滚到指定版本

```bash
kubectl -n freshmall rollout history deploy/freshmall-order
kubectl -n freshmall rollout undo deploy/freshmall-order --to-revision=<n>
```

## 7. order 从 outbox 到 MQ 的演进

当前：`outbox + scheduler + retry + thing feign`。

建议分阶段：

1. 保留 outbox 入库，增加 relay 发布到 RabbitMQ。
2. 增加 MQ 消费者，按 `eventId` 做幂等。
3. 灰度期双通道（outbox 保底，MQ 主路径）。
4. 稳定后收敛旧路径，仅保留必要兜底。

配套建议：

- 统一 exchange/queue/routing key 命名。
- 增加 DLQ 和重试策略。
- 增加消费失败告警和消息积压监控。

## 8. 常见坑

- 现象：`spring.rabbitmq.druid` 报错。
- 原因：YAML 缩进错误，把 `rabbitmq` 写到了 `datasource` 下面。
- 正确：`spring.datasource.druid` 与 `spring.rabbitmq` 必须是并列层级。
