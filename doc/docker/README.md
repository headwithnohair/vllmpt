# Docker 环境配置说明

## 📋 包含的服务

| 服务 | 版本 | 端口 | 用途 |
|------|------|------|------|
| **Redis** | 7.x | 6379 | 缓存、会话存储、限流计数 |
| **RocketMQ** | 5.3.2 | 9876, 10911, 8180 | 消息队列、异步任务（NameServer + Broker + Dashboard） |
| **Elasticsearch** | 8.11 | 9200, 9300 | 日志存储、全文检索 |
| **Kibana** | 8.11 | 5601 | 日志可视化、数据分析 |
| **Logstash** | 8.11 | 5044, 9600 | 日志收集、处理 |
| **MinIO** | Latest | 9000, 9001 | 对象存储（图片、文件） |

---

## 🚀 快速启动

### 1. 启动所有服务

```bash
# 进入 docker 目录
cd doc/docker

# 启动所有服务（后台运行）
docker-compose -f docker-compose-env.yml up -d

# 查看启动日志
docker-compose -f docker-compose-env.yml logs -f
```

### 2. 停止所有服务

```bash
# 停止所有服务
docker-compose -f docker-compose-env.yml down

# 停止并删除数据卷（谨慎使用！）
docker-compose -f docker-compose-env.yml down -v
```

### 3. 重启某个服务

```bash
# 重启 Redis
docker-compose -f docker-compose-env.yml restart redis

# 重启 Elasticsearch
docker-compose -f docker-compose-env.yml restart elasticsearch
```

---

## 🔧 服务访问信息

### Redis
- **地址**: `localhost:6379`
- **密码**: `redis123`
- **连接命令**: `redis-cli -h localhost -p 6379 -a redis123`

### RocketMQ
- **NameServer 地址**: `localhost:9876`
- **Broker 端口**: `10911`（客户端收发）、`10909`（VIP 通道）
- **Dashboard 管理界面**: http://localhost:8180
- **说明**: 单机单 Master；Broker 配置见 `rocketmq/broker.conf`（brokerIP1=127.0.0.1）

### Elasticsearch
- **地址**: `http://localhost:9200`
- **测试连接**: `curl http://localhost:9200`
- **安全认证**: 已禁用（开发环境）

### Kibana
- **地址**: http://localhost:5601
- **Elasticsearch 地址**: `http://elasticsearch:9200`（容器内）

### Logstash
- **TCP 输入端口**: `5044`
- **Beats 输入端口**: `5045`
- **监控 API**: http://localhost:9600

### MinIO
- **API 地址**: http://localhost:9000
- **Console 地址**: http://localhost:9001
- **用户名**: `minioadmin`
- **密码**: `minio123`

---

## 📊 健康检查

带健康检查的服务（如 Redis、MySQL）可通过以下命令查看健康状态：

```bash
# 查看所有服务状态
docker-compose -f docker-compose-env.yml ps

# 查看某个服务的健康状态
docker inspect --format='{{.State.Health.Status}}' vllmpt-redis
```

---

## 💾 数据持久化

所有数据都存储在 Docker Volume 中，即使容器删除，数据也不会丢失。

**数据卷列表**：
- `redis_data` - Redis 数据
- `rocketmq_broker_store` - RocketMQ Broker 消息存储（commitlog / consumequeue）
- `rocketmq_broker_logs` - RocketMQ Broker 日志
- `rocketmq_namesrv_logs` - RocketMQ NameServer 日志
- `es_data` - Elasticsearch 数据
- `minio_data` - MinIO 数据

**查看数据卷**：
```bash
docker volume ls | grep vllmpt
```

**备份数据**：
```bash
# 备份 Elasticsearch 数据
docker run --rm -v vllmpt-docker_es_data:/data -v $(pwd):/backup alpine tar czf /backup/es-data-backup.tar.gz -C /data .
```

---

## 🔍 日志查看

### 查看所有服务日志
```bash
docker-compose -f docker-compose-env.yml logs -f
```

### 查看单个服务日志
```bash
docker-compose -f docker-compose-env.yml logs -f redis
docker-compose -f docker-compose-env.yml logs -f elasticsearch
```

### 查看最近 100 行日志
```bash
docker-compose -f docker-compose-env.yml logs --tail=100 rocketmq-broker
```

---

## 🛠️ 常见问题

### Q1: Elasticsearch 启动失败？

**原因**：内存不足或 vm.max_map_count 设置不正确

**解决方案**：
```bash
# Windows/Mac: 增加 Docker 内存限制到至少 4GB

# Linux: 修改系统参数
sudo sysctl -w vm.max_map_count=262144
```

### Q2: 端口冲突？

**症状**：`Bind for 0.0.0.0:6379 failed: port is already allocated`

**解决方案**：
1. 找到占用端口的进程并停止
2. 或者修改 `docker-compose-env.yml` 中的端口映射

```yaml
# 例如将 Redis 端口改为 6380
ports:
  - "6380:6379"
```

### Q3: 服务启动很慢？

**原因**：首次启动需要下载镜像

**解决方案**：
```bash
# 预先下载所有镜像
docker-compose -f docker-compose-env.yml pull
```

### Q4: 如何重置所有数据？

**警告**：这会删除所有数据！

```bash
docker-compose -f docker-compose-env.yml down -v
docker-compose -f docker-compose-env.yml up -d
```

---

## 📝 Logstash 配置

Logstash 配置文件位于：`logstash/pipeline/logstash.conf`

**修改配置后重启 Logstash**：
```bash
docker-compose -f docker-compose-env.yml restart logstash
```

**查看 Logstash 日志**：
```bash
docker-compose -f docker-compose-env.yml logs -f logstash
```

---

## 🔐 安全建议（生产环境）

1. **修改默认密码 / 开启认证**
   - Redis 密码
   - RocketMQ 开启 ACL（默认未开启）
   - MinIO 用户名/密码

2. **启用 Elasticsearch 安全认证**
   ```yaml
   environment:
     - xpack.security.enabled=true
     - ELASTIC_PASSWORD=your_password
   ```

3. **限制网络访问**
   - 只暴露必要的端口
   - 使用防火墙规则限制访问 IP

4. **使用 HTTPS**
   - 为 MinIO、Kibana 等配置 SSL 证书

---

## 📚 相关文档

- [Redis 官方文档](https://redis.io/documentation)
- [RocketMQ 官方文档](https://rocketmq.apache.org/docs/)
- [Elasticsearch 官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [Kibana 官方文档](https://www.elastic.co/guide/en/kibana/current/index.html)
- [Logstash 官方文档](https://www.elastic.co/guide/en/logstash/current/index.html)
- [MinIO 官方文档](https://docs.min.io/)

---

**最后更新**: 2026-05-19
