# 数据持久化与常用命令

这篇文档从实际操作出发，解释每条常用命令会创建、停止或删除什么。生产部署、备份和回滚仍以 [Docker 与 Docker Compose 部署指南](../../DOCKER_DEPLOYMENT_GUIDE.md) 为准。

## 1. 建议先定义一个命令别名

Compose 命令必须始终带上正确的环境文件和两个 Compose 文件。为了减少漏写或写错，可以在当前 shell 中定义一个函数。

本地环境：

```bash
dc_local() {
  docker compose --env-file .env \
    -f compose.yaml -f compose.local.yaml "$@"
}
```

之后可以执行：

```bash
dc_local config --quiet
dc_local up -d --build
dc_local ps
dc_local logs -f backend
dc_local down
```

这个函数只在当前 shell 会话中存在，不会修改项目文件，也不会改变 Compose 行为。

生产环境不要复用本地函数，应明确指向工作区外的真实环境文件：

```bash
ENV_FILE=/etc/picture-zip-upload/production.env

dc_prod() {
  docker compose --env-file "$ENV_FILE" \
    -f compose.yaml -f compose.prod.yaml "$@"
}
```

## 2. 第一次本地启动的完整过程

### 2.1 创建本地变量文件

```bash
cp .env.example .env
```

`.env.example` 是可提交的模板，`.env` 是本机实际配置并被 Git 和 Docker 构建上下文排除。即使是本地环境，也建议修改模板中的示例密码。

### 2.2 只检查，不启动

```bash
dc_local config --quiet
dc_local config --services
```

- `config --quiet` 只校验语法、变量和合并结果，成功时通常没有输出。
- `config --services` 显示默认会参与正常启动的服务。

预期服务为：

```text
mysql
redis
backend
```

看不到 `maintenance` 是正常的，因为它位于 profile 中。

### 2.3 构建并后台启动

```bash
dc_local up -d --build
```

可以拆开理解：

- `up`：使实际运行状态接近 Compose 描述的目标状态。
- `-d`：后台运行，不持续占用当前终端。
- `--build`：启动前重新检查并构建需要构建的镜像。

第一次执行时通常会发生：

1. 拉取 Maven、JRE、MySQL 和 Redis 基础镜像。
2. 构建本项目后端镜像。
3. 创建 Compose bridge 网络。
4. 创建五个本地命名卷。
5. 启动 MySQL 和 Redis。
6. MySQL 首次初始化时执行 `db/schema.sql`。
7. 等 MySQL/Redis 健康后启动后端。
8. 后端通过 Actuator 健康检查后被标记为 `healthy`。

首次启动比以后慢是正常现象，因为要下载镜像、Maven 依赖并初始化数据库。

### 2.4 查看状态和日志

```bash
dc_local ps
dc_local logs --tail=200 backend
dc_local logs -f backend
```

- `ps` 查看服务容器状态和健康状态。
- `logs --tail=200` 查看最近 200 行。
- `logs -f` 持续跟随新日志，按 `Ctrl+C` 只会退出日志查看，不会停止容器。

检查 HTTP 健康接口：

```bash
curl --fail http://127.0.0.1:8080/actuator/health
```

## 3. 构建、创建、启动分别是什么

这些动作经常被混为一谈：

| 动作 | 典型命令 | 改变了什么 |
| --- | --- | --- |
| 构建镜像 | `dc_local build backend` | 从 Dockerfile 生成/更新镜像，不启动容器 |
| 创建容器 | `dc_local create` | 根据配置创建容器，不启动进程 |
| 启动已有容器 | `dc_local start` | 启动已存在的已停止容器 |
| 创建并启动 | `dc_local up -d` | 必要时创建或重建，然后启动 |
| 重启进程 | `dc_local restart backend` | 停止再启动现有容器，不重新构建镜像 |
| 重建并启动 | `dc_local up -d --build backend` | 先构建，再按需重建后端容器 |

一个常见误区是：修改 Java 源码后执行 `restart`。`restart` 不会重新运行 Dockerfile，旧容器仍然使用旧镜像。源码变化后应执行：

```bash
dc_local up -d --build backend
```

如果只修改 `.env` 或 Compose 的容器配置，也应使用 `up -d` 让 Compose 判断是否需要重建容器；单纯 `restart` 不会把新的容器环境变量写入已创建的容器。

## 4. 停止与删除的区别

### 4.1 暂停运行但保留容器

```bash
dc_local stop
```

它停止服务容器，但保留容器、网络和命名卷。之后可以执行 `dc_local start`。

### 4.2 删除容器和项目网络，但保留数据卷

```bash
dc_local down
```

默认效果：

- 停止并删除 Compose 服务容器。
- 删除 Compose 创建的默认项目网络。
- 保留镜像。
- 保留命名卷。

下次执行 `up` 会创建新容器，但 MySQL、Redis 和图片数据仍能从命名卷中恢复。

### 4.3 删除容器和本地命名卷

```bash
dc_local down -v
```

`-v` 会额外删除这个 Compose 项目声明的命名卷，包括：

- MySQL 数据。
- Redis AOF 数据。
- 上传工作目录数据。
- 新图片数据。
- 本地历史图片卷数据。

这是一个数据删除命令，只能在确认本地模拟数据可以丢弃时执行。它不应出现在生产操作流程中。

### 4.4 删除镜像

`down` 不会删除后端镜像。查看镜像：

```bash
docker image ls picture-zip-upload
```

删除镜像要使用独立的 `docker image rm`。镜像删除和数据卷删除是两件不同的事。

## 5. 数据到底保存在哪里

### 5.1 本地命名卷

本地 Compose 顶层声明五个卷。实际名称通常会带 Compose 项目前缀，例如：

```text
picture-zip-upload-local_mysql-data
```

列出当前 Docker 中的卷：

```bash
docker volume ls
```

查看 Compose 解析后的卷名和挂载关系：

```bash
dc_local config
docker inspect 容器名
```

不建议直接进入 Docker 内部存储目录修改命名卷。需要查看数据时，优先通过 MySQL/Redis 客户端、应用接口，或临时只读容器访问。

### 5.2 生产绑定挂载

生产环境文件明确提供宿主机路径：

```dotenv
PICTURE_WORK_HOST_PATH=/data/picture-upload-work
PICTURE_IMAGE_HOST_PATH=/data/pictures
PICTURE_LEGACY_HOST_PATH=/data/corpusImages
```

Compose 把它们挂到容器中的固定目录。容器被删除后，宿主机目录不随之删除。

但是“数据不会随容器删除”不等于“数据已经备份”。应用或人工操作仍可能修改可写目录，所以生产必须执行独立备份和恢复演练。

### 5.3 为什么镜像里的目录权限可能不生效

Dockerfile 已经创建 `/data/pictures` 并把它交给 `app` 用户。但生产绑定挂载后，宿主机 `/data/pictures` 会覆盖镜像中的目录视图，最终权限取决于宿主机：

```bash
sudo install -d -o 10001 -g 10001 /data/picture-upload-work /data/pictures
sudo test -w /data/picture-upload-work
sudo test -w /data/pictures
sudo test -r /data/corpusImages
```

如果后端日志出现 `Permission denied`，应同时检查：

- 容器进程是不是 UID/GID `10001`。
- 宿主机目录的 owner、group 和 mode。
- 该挂载是否意外带有 `:ro`。
- 上级目录是否允许 UID `10001` 进入。

查看容器用户：

```bash
dc_local exec backend id
```

## 6. 进入容器和执行命令

### 6.1 在正在运行的服务容器中执行命令

```bash
dc_local exec backend id
dc_local exec backend sh
```

`exec` 不创建新容器，而是在现有容器中启动一个额外进程。运行时镜像可能没有 Bash，因此这里使用 `sh`。

由于根文件系统只读、用户不是 root 且 capabilities 已删除，不能期待在运行容器中临时安装软件。这是正常的安全设计。需要新工具时应修改 Dockerfile 并重新构建镜像。

### 6.2 运行一次性维护任务

```bash
MAINTENANCE_MODE=IMPORT_DIRECT \
MAINTENANCE_DRY_RUN=true \
MAINTENANCE_BATCH_ID=local-check \
dc_local --profile maintenance run --rm maintenance
```

这里命令前的三个变量只对本次命令生效，并覆盖 `.env` 中同名值。维护任务默认 dry-run；正式执行前应遵循维护脚本指南中的备份、报告检查和回滚要求。

## 7. 查看最终生效配置

### 7.1 查看全部合并结果

```bash
dc_local config
```

当你怀疑某个值没生效时，不要只看原始 YAML。三个重要问题应从最终配置回答：

1. 变量有没有被替换成预期值？
2. 后一个 Compose 文件有没有覆盖前一个文件？
3. 配置最终进入了哪个服务？

注意：完整 `config` 输出可能包含已替换的密码，不要把输出直接粘贴到工单、聊天或公开日志中。

### 7.2 查看容器实际环境变量

```bash
dc_local exec backend env | sort
```

这个命令同样可能输出密码，只适合在受控终端排查。更安全的做法是只查非敏感变量：

```bash
dc_local exec backend sh -c 'printf "%s\n" "$SERVER_PORT" "$REDIS_HOST" "$PICTURE_IMAGE_ROOT"'
```

### 7.3 区分 Compose 变量和 Spring 变量

如果 `config` 输出正确，但 Java 行为不正确，继续检查：

- 容器是否在修改后被重新创建。
- 环境变量名是否与 `application.yml` 一致。
- Spring 日志是否显示其他 profile 或外部配置覆盖了它。
- 密码中是否有需要正确引用的特殊字符。

## 8. 常见问题排查顺序

### 后端容器没有启动

```bash
dc_local ps -a
dc_local logs --tail=200 backend
dc_local logs --tail=100 mysql redis
```

先看后端是否在等待依赖健康，再看 MySQL/Redis 的健康检查和日志。

### 后端显示 `unhealthy`

```bash
dc_local logs --tail=200 backend
curl -v http://127.0.0.1:8080/actuator/health
```

常见方向包括应用仍在启动、数据库连接失败、端口不一致或 Actuator 没有正常响应。

### 修改源码后行为没有变化

```bash
dc_local up -d --build backend
dc_local logs --tail=100 backend
```

确认构建日志确实重新执行 Maven，并确认后端容器的创建时间已经更新。

### 修改 `.env` 后行为没有变化

```bash
dc_local config
dc_local up -d backend
```

`restart` 不会更新一个已创建容器的环境变量；`up -d` 会根据配置差异重建。

### MySQL 修改了 `schema.sql` 但没有变化

初始化脚本只在全新空数据卷上运行。应使用正常数据库迁移或手工执行经过审查的 SQL。只有确认本地数据可删除时，才考虑删除本地 MySQL 卷重新初始化。

### 端口被占用

修改 `.env` 中宿主机端口，例如：

```dotenv
BACKEND_PORT=18080
MYSQL_EXPOSED_PORT=13307
REDIS_EXPOSED_PORT=16380
```

容器内端口不需要跟着改。后端容器仍监听 8080，MySQL/Redis 容器仍监听 3306/6379。

## 9. 新手安全练习

下面这组操作只使用本地环境，适合观察 Compose 行为：

```bash
# 1. 渲染配置，不产生容器
dc_local config --services

# 2. 启动
dc_local up -d --build

# 3. 观察服务和日志
dc_local ps
dc_local logs --tail=50 backend

# 4. 停止并删除容器，但保留卷
dc_local down

# 5. 再次启动，观察数据库数据仍然存在
dc_local up -d

# 6. 完成后再次停止，仍保留本地数据
dc_local down
```

练习时不要执行 `down -v`，这样可以亲自验证“容器生命周期”和“数据生命周期”是分开的。
