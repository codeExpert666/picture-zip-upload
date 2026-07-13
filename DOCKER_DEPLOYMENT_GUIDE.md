# Docker 与 Docker Compose 部署指南

## 1. 部署边界

本仓库采用两套运行拓扑：

- 本地/测试：`backend + MySQL 5.7.44 + Redis 5.0.14` 全部进入 Compose，用 Linux/amd64 容器模拟 Ubuntu 服务器。
- 生产首阶段：Compose 只管理后端；现有宿主机 MySQL、Redis、Nginx 和其中的数据保持原状。

`compose.prod.yaml` 没有 MySQL/Redis service，也没有 `/var/lib/mysql` 挂载或数据库初始化 SQL。不要把生产数据库目录直接挂进本地 Compose 的 MySQL 容器。

当前仓库不包含 Vue 源码，因此不会声明一个不可构建的前端 service。`docker/frontend/` 提供应复制到公司 Vue 仓库的 Node 24.13.0 + Nginx 模板；前端构建产物使用相对 `/api` 地址。生产仍由宿主机 Nginx 管理域名和 TLS。

## 2. 文件职责

执行环境要求 Docker Engine 24+、BuildKit 和 Docker Compose v2；Windows 使用当前受支持的 Docker Desktop。Dockerfile 的缓存挂载和可选 secret 均依赖 BuildKit。

- `Dockerfile`：Maven 3.9.0 + Java 17 多阶段后端镜像，运行时使用非 root 用户。
- `compose.yaml`：后端与按需维护任务的公共安全、日志和构建配置。
- `compose.local.yaml`：本地 MySQL、Redis、持久卷和端口。
- `compose.prod.yaml`：Ubuntu 生产覆盖，只连接宿主机服务和真实数据目录。
- `.env.example`：可直接复制的本地变量模板。
- `.env.prod.example`：生产变量结构示例，不得直接作为真实凭据使用。
- `docker/nginx/picture-zip-upload.conf.example`：应合并进现有宿主机 Nginx 的 upstream/location 示例。

当前后端、Maven、MySQL 和 Redis 示例同时固定了可读版本标签和已验证的 manifest digest。正式发布时应把这些镜像同步到公司镜像仓库，再将变量替换为公司仓库内的不可变引用。

若公司网络必须通过 Maven 仓库代理，优先在 CI 中通过受控 `settings.xml` 和 BuildKit secret 配置，不要把仓库账号、密码写入 `MAVEN_BUILD_OPTS`、Dockerfile 或 Git。Dockerfile 支持可选的 `maven_settings` secret：

```bash
docker build \
  --secret id=maven_settings,src=/secure/path/settings.xml \
  --tag picture-zip-upload:release .
```

`settings.xml` 只在 Maven 构建步骤临时挂载，不会复制进镜像层。`MAVEN_BUILD_OPTS` 只预留给不含凭据的 JVM 网络参数。

## 3. 本地完整环境

复制本地配置：

```bash
cp .env.example .env
```

`.env` 内是隔离的样例密码，不应复用公司环境密码。MySQL 5.7 官方镜像只提供 linux/amd64，因此默认在 Windows Docker Desktop、Intel Ubuntu 或 Apple Silicon 模拟模式下运行 linux/amd64。

本地镜像实测为 Redis 5.0.14 + `jemalloc-5.1.0`；公司服务器报告的是 jemalloc 5.2.1。本方案选择业务功能版本对齐，不把 jemalloc 二进制差异误称为完全一致。若后续问题与内存碎片、RSS 或 allocator 行为相关，应在 Ubuntu 服务器同版本环境复现，或另建严格编译参数对齐的 Redis 镜像。

启动并检查：

```bash
docker compose --env-file .env \
  -f compose.yaml -f compose.local.yaml \
  up -d --build

docker compose --env-file .env \
  -f compose.yaml -f compose.local.yaml \
  ps

curl --fail http://127.0.0.1:8080/actuator/health
```

首次创建 MySQL volume 时会自动执行 `db/schema.sql`；已有 volume 不会重复初始化。MySQL 和 Redis 只绑定到 `127.0.0.1:3307`、`127.0.0.1:6380`，不会向局域网开放。

停止但保留数据：

```bash
docker compose --env-file .env \
  -f compose.yaml -f compose.local.yaml down
```

只有确认删除的是本地模拟数据时才能执行 `down -v`。生产环境禁止使用该命令。

## 4. 生产切换前硬性检查

### 4.1 数据库和文件备份

先查看非事务表：

```sql
SELECT TABLE_SCHEMA, TABLE_NAME, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'ai_dataset'
  AND ENGINE <> 'InnoDB';
```

全部关键表为 InnoDB 时，可以在低峰期使用现有备份系统，或进行一致性逻辑备份：

```bash
mysqldump --host=127.0.0.1 --user=backup_operator -p \
  --single-transaction --quick --routines --triggers --events --hex-blob \
  ai_dataset > "ai_dataset-$(date +%Y%m%d-%H%M%S).sql"
```

若存在 MyISAM 等非事务表，应使用公司已有物理备份，或安排停写/锁表窗口，不能把 `--single-transaction` 当成全库一致性保证。还必须单独备份以下目录：

- `/data/picture-upload-work`
- `/data/pictures`
- `/data/corpusImages`

备份完成后必须在隔离 MySQL 中恢复，核对表/记录数量、关键业务数据和图片关联；只有恢复演练通过才允许切换。生产数据不得导入开发人员电脑，测试演练使用脱敏数据。

### 4.2 宿主机准备

后端镜像固定使用 UID/GID `10001`。Ubuntu 上创建并校验目录权限：

```bash
sudo install -d -o 10001 -g 10001 /data/picture-upload-work /data/pictures
sudo test -r /data/corpusImages
sudo test -w /data/picture-upload-work
sudo test -w /data/pictures
```

旧图目录在容器内只读；工作目录和新图片目录可写。还要确认磁盘空间、inode、MySQL/Redis备份、旧后端启动方式及 Nginx 回切配置均可用。

## 5. 生产部署

把真实环境文件保存在 Git 工作区之外：

```bash
sudo install -d -m 700 /etc/picture-zip-upload
sudo cp .env.prod.example /etc/picture-zip-upload/production.env
sudo chmod 600 /etc/picture-zip-upload/production.env
sudoedit /etc/picture-zip-upload/production.env
```

生产 `MYSQL_URL` 和 `REDIS_HOST` 使用 `127.0.0.1`。后端在 Ubuntu 上使用 host network，并只监听 `127.0.0.1:18080`；这避免为了容器网络修改现有 MySQL/Redis 的监听地址和防火墙规则。

先渲染配置，确认输出中不存在 MySQL/Redis service：

```bash
ENV_FILE=/etc/picture-zip-upload/production.env

docker compose --env-file "$ENV_FILE" \
  -f compose.yaml -f compose.prod.yaml \
  config --quiet

docker compose --env-file "$ENV_FILE" \
  -f compose.yaml -f compose.prod.yaml \
  config --services
```

输出应只有正常启动的 `backend`；维护 service 位于 profile 中，不会常驻启动。部署版本化镜像后启动：

```bash
docker compose --env-file "$ENV_FILE" \
  -f compose.yaml -f compose.prod.yaml \
  up -d --no-deps backend

curl --fail http://127.0.0.1:18080/actuator/health
docker compose --env-file "$ENV_FILE" \
  -f compose.yaml -f compose.prod.yaml \
  logs --tail=200 backend
```

健康检查通过后，把 `docker/nginx/picture-zip-upload.conf.example` 中的 upstream/location 合并到现有 HTTPS virtual host：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

不要从公网代理 `/actuator/**`。切换后验证创建任务、上传分片、完成导入、进度查询、新旧图片 URL 和 Redis进度恢复。

## 6. 回滚

首次发布保留旧后端进程、旧前端静态文件和旧 Nginx upstream。出现错误时：

1. 将 Nginx upstream 切回旧后端端口并执行 `nginx -t`、reload。
2. 停止新容器：`docker compose ... stop backend`。
3. 保留新容器日志、数据库和图片目录现场，不运行清理或反向迁移脚本。

本次切换不修改数据库结构、不迁移数据库数据目录，因此应用回滚不需要数据库回滚。MySQL 5.7 升级或数据库容器化必须另立迁移窗口和回切方案。

## 7. 容器化维护任务

维护 service 默认 `dry-run=true`，不会随正常 Compose 启动。生产先执行 dry-run：

```bash
ENV_FILE=/etc/picture-zip-upload/production.env

MAINTENANCE_MODE=IMPORT_DIRECT \
MAINTENANCE_DRY_RUN=true \
MAINTENANCE_BATCH_ID=import-check-20260713 \
docker compose --env-file "$ENV_FILE" \
  -f compose.yaml -f compose.prod.yaml \
  --profile maintenance run --rm maintenance
```

检查报告和数据库备份后，才可把单次命令改为 `MAINTENANCE_DRY_RUN=false`。历史回填使用 `MAINTENANCE_MODE=BACKFILL_EXISTING`，详细数据校验和回滚步骤仍以 `PICTURE_MAINTENANCE_SCRIPTS_GUIDE.md` 为准。

## 8. Vue 前端仓库对接

把以下模板复制到真实 Vue 3 仓库：

```text
docker/frontend/Dockerfile.example  -> Dockerfile
docker/frontend/nginx.conf.example  -> docker/nginx.conf
```

模板要求存在 `package-lock.json`，使用 Node 24.13.0 执行 `npm ci && npm run build`，再由非 root Nginx 在 8080 端口提供 `dist`。前端请求后端时使用相对 `/api`；生产映射建议为 `127.0.0.1:18081:8080`，再由现有宿主机 Nginx统一终止 TLS。
