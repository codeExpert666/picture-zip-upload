# Compose 配置逐段解析

Dockerfile 只定义一个后端镜像怎样构建。Compose 负责定义：要运行哪些服务、给它们哪些环境变量、怎样连接网络、怎样保存数据，以及怎样启动和停止。

本项目不会单独使用某一个 Compose 文件，而是按环境组合：

```bash
# 本地
docker compose --env-file .env \
  -f compose.yaml -f compose.local.yaml ...

# 生产
docker compose --env-file /etc/picture-zip-upload/production.env \
  -f compose.yaml -f compose.prod.yaml ...
```

## 1. 先理解变量替换语法

Compose 在创建容器之前处理 `${...}`。本项目常见的形式有三种：

| 写法 | 含义 | 示例 |
| --- | --- | --- |
| `${NAME}` | 直接读取变量；未设置时通常替换为空并警告 | `${REDIS_PASSWORD}` |
| `${NAME:-value}` | 未设置或为空时使用默认值 | `${BACKEND_PORT:-8080}` |
| `${NAME:?message}` | 未设置或为空时立即报错 | `${MYSQL_URL:?MYSQL_URL must ...}` |

`${NAME:?message}` 是生产安全保护。缺少数据库地址、账号或宿主机目录时，Compose 在渲染配置阶段就失败，不会带着空配置启动容器。

可以用只读命令查看变量替换和文件合并后的结果：

```bash
docker compose --env-file .env \
  -f compose.yaml -f compose.local.yaml \
  config
```

`.env` 主要为 Compose 的变量替换提供值。它并不会无条件把所有变量都塞进每个容器；容器最终得到哪些变量，仍由各服务的 `environment` 等配置决定。

### 为什么有时写 `$${NAME}`？

`compose.local.yaml` 的 MySQL 健康检查包含：

```yaml
test: ["CMD-SHELL", "mysqladmin ... --password=\"$${MYSQL_ROOT_PASSWORD}\" ..."]
```

双美元符号 `$$` 告诉 Compose：“现在不要替换，把一个 `$` 留给容器内的 shell。”最终在 MySQL 容器中执行的是 `${MYSQL_ROOT_PASSWORD}`，读取的是容器自身环境变量。

如果这里只有一个 `$`，Compose 会过早地尝试读取宿主机变量，容易出现空密码或错误的健康检查命令。

## 2. `compose.yaml`：公共配置

### 2.1 Compose 项目名

```yaml
name: ${COMPOSE_PROJECT_NAME:-picture-zip-upload}
```

项目名用于隔离同一台机器上的不同 Compose 项目，并参与生成容器、网络和命名卷的实际名称。

本地 `.env` 使用 `picture-zip-upload-local`，生产示例使用 `picture-zip-upload-prod`，可以降低误操作到另一套环境的风险。

### 2.2 `x-` 扩展字段和 YAML 锚点

```yaml
x-app-environment: &app-environment
  TZ: ...
  JAVA_TOOL_OPTIONS: ...

x-app-service: &app-service
  image: ...
  ...
```

以 `x-` 开头的是 Compose 扩展字段，不会创建服务。`&app-environment` 和 `&app-service` 是 YAML 锚点，相当于给一段配置起名字。

后面通过 `*` 和合并键复用：

```yaml
backend:
  <<: *app-service
  environment:
    <<: *app-environment
```

这不是 Docker 的继承机制，而是 YAML 在解析文件时展开重复配置。它让 `backend` 和 `maintenance` 共用相同的构建、安全和日志设置。

### 2.3 镜像和构建

```yaml
image: ${BACKEND_IMAGE:-picture-zip-upload:local}
platform: ${DOCKER_PLATFORM:-linux/amd64}
build:
  context: .
  args:
    MAVEN_IMAGE: ...
    RUNTIME_IMAGE: ...
    MAVEN_OPTS: ${MAVEN_BUILD_OPTS:-}
    APP_UID: ${APP_UID:-10001}
    APP_GID: ${APP_GID:-10001}
```

- `image` 指定构建完成后的镜像名，或启动时要使用的镜像名。
- `platform` 固定 Linux/amd64。
- `build.context` 指向项目根目录。
- `build.args` 把 Compose 变量传给 Dockerfile 的 `ARG`。

这里要区分构建参数和运行环境变量：

| 类型 | 生效时间 | 本项目示例 |
| --- | --- | --- |
| `build.args` / Dockerfile `ARG` | 构建镜像时 | `APP_UID`、`MAVEN_IMAGE` |
| Dockerfile `ENV` | 镜像和容器运行时默认值 | `SERVER_PORT` |
| Compose `environment` | 创建容器时，可覆盖镜像默认值 | `MYSQL_URL`、`REDIS_HOST` |

不要用 `build.args` 传密码，因为构建参数不是安全的 secret 机制。

### 2.4 只读根文件系统和临时目录

```yaml
read_only: true
tmpfs:
  - /tmp:rw,noexec,nosuid,size=256m,uid=10001,gid=10001
```

`read_only: true` 使容器根文件系统在运行时只读。应用不能随意修改镜像中的 `/app` 或系统目录。

Java 和系统工具有时需要临时文件，因此单独提供 `/tmp` 内存文件系统：

- `rw`：允许读写。
- `noexec`：禁止从该目录直接执行程序。
- `nosuid`：忽略 setuid/setgid 权限位。
- `size=256m`：限制最多使用 256 MiB。
- `uid/gid`：让非 root 应用用户能够写入。

业务数据目录由命名卷或绑定挂载提供，不依赖容器根文件系统写入。

### 2.5 Linux 权限收紧

```yaml
cap_drop:
  - ALL
security_opt:
  - no-new-privileges:true
```

- `cap_drop: ALL` 删除容器进程默认拥有的 Linux capabilities。
- `no-new-privileges` 阻止进程通过 setuid 等方式获得更多权限。

这和 Dockerfile 中的 `USER app:app` 共同组成分层防护：应用既不是 root，也没有额外 Linux capability。

### 2.6 停机和日志

```yaml
stop_grace_period: 60s
logging:
  driver: json-file
  options:
    max-size: ${LOG_MAX_SIZE:-10m}
    max-file: ${LOG_MAX_FILES:-5}
```

`docker compose stop` 时，Docker 先发送 `SIGTERM`，最多等待 60 秒，再考虑强制终止。它与 Spring Boot 的优雅停机配置配合。

`json-file` 日志驱动会保存容器标准输出和标准错误。轮转配置表示：

- 单个日志文件默认最大 10 MiB。
- 默认最多保留 5 个文件。

它避免长期运行的容器日志无限占用磁盘，但不等同于完整的公司日志采集和归档方案。

### 2.7 `backend` 服务

```yaml
backend:
  <<: *app-service
  environment:
    <<: *app-environment
  restart: unless-stopped
```

公共文件只给后端放入所有环境都需要的时区、JVM 和停机配置。数据库、Redis、目录等环境差异留给 local/prod 文件。

`restart: unless-stopped` 表示进程异常退出或 Docker daemon 重启后通常会自动恢复；如果管理员明确手动停止了容器，则保持停止。

### 2.8 `maintenance` 服务和 profile

```yaml
maintenance:
  <<: *app-service
  profiles:
    - maintenance
  environment:
    SPRING_MAIN_WEB_APPLICATION_TYPE: none
    PICTURE_MAINTENANCE_ENABLED: "true"
    PICTURE_MAINTENANCE_DRY_RUN: ${MAINTENANCE_DRY_RUN:-true}
    ...
  restart: "no"
  healthcheck:
    disable: true
```

维护服务和后端使用同一个镜像，但用环境变量把 Java 应用切换成一次性维护任务：

- 不启动 Web 服务。
- 默认启用维护功能。
- 默认 `dry-run=true`，只预演不正式修改。
- 任务结束后不自动重启。
- 因为没有 Web 服务，所以禁用 Actuator HTTP 健康检查。

`profiles: maintenance` 表示普通 `up` 不会启动它。需要明确执行：

```bash
docker compose ... --profile maintenance run --rm maintenance
```

`run` 创建一次性容器，`--rm` 在命令结束后删除这个容器。数据仍位于外部卷或宿主机目录中，不会随一次性容器删除。

## 3. `compose.local.yaml`：本地完整环境

### 3.1 MySQL 服务

```yaml
mysql:
  image: mysql:5.7.44@sha256:...
  environment:
    MYSQL_ROOT_PASSWORD: ...
    MYSQL_DATABASE: ...
    MYSQL_USER: ...
    MYSQL_PASSWORD: ...
```

这些变量由 MySQL 官方镜像的启动脚本读取，用于第一次初始化数据目录。它们不是 Spring Boot 的环境变量。

MySQL 启动参数：

```yaml
command:
  - --character-set-server=utf8mb4
  - --collation-server=utf8mb4_unicode_ci
  - --max-allowed-packet=256M
```

它们设置默认字符集、排序规则和最大数据包，适配中文文本及较大的数据库请求。

端口映射：

```yaml
ports:
  - 127.0.0.1:${MYSQL_EXPOSED_PORT:-3307}:3306
```

格式是：

```text
宿主机监听地址 : 宿主机端口 : 容器端口
127.0.0.1      : 3307       : 3306
```

因此本机数据库工具连接 `127.0.0.1:3307`；后端容器不走这个端口，而是在 Compose 网络中连接 `mysql:3306`。

只绑定 `127.0.0.1` 表示不会直接向局域网其他机器开放。

数据挂载：

```yaml
volumes:
  - mysql-data:/var/lib/mysql
  - ./db/schema.sql:/docker-entrypoint-initdb.d/001-schema.sql:ro
```

- `mysql-data` 保存 MySQL 实际数据文件。
- `schema.sql` 以只读方式挂入初始化目录。
- 初始化 SQL 只在 `mysql-data` 第一次创建、数据目录为空时执行。
- 修改 `schema.sql` 后重启一个已有 MySQL 卷，不会自动重新执行它。

这也是为什么不能用“不断重启容器”来测试数据库初始化变化。如果确认本地数据可以删除，需要显式删除本地卷后重新创建；生产环境绝对不能照搬这个做法。

健康检查使用 `mysqladmin ping`。只有 MySQL 被标记为健康，依赖它的后端才会启动。

### 3.2 Redis 服务

Redis 使用下面的命令启用 AOF 和密码：

```yaml
command: >-
  sh -c 'exec redis-server --appendonly yes --requirepass "$${REDIS_PASSWORD}"'
```

- YAML 的 `>-` 把后续多行折叠成一行字符串。
- `sh -c` 让容器内 shell 展开 `REDIS_PASSWORD`。
- `exec` 让 `redis-server` 替换 shell，成为容器主进程，便于接收停止信号。
- `--appendonly yes` 开启 AOF 持久化。
- `--requirepass` 启用密码。

Redis 数据写入 `redis-data:/data`。宿主机端口是 `127.0.0.1:6380`，容器内端口仍是 `6379`。

健康检查执行经过认证的 `PING`，并确认结果包含 `PONG`。

### 3.3 本地后端服务

local 文件为公共 `backend` 补充本地环境变量：

```yaml
MYSQL_URL: jdbc:mysql://mysql:3306/...
REDIS_HOST: redis
REDIS_PORT: 6379
```

`mysql` 和 `redis` 是服务名，也是 Compose 网络中的 DNS 名称。容器 IP 可能变化，服务名保持稳定。

启动依赖：

```yaml
depends_on:
  mysql:
    condition: service_healthy
  redis:
    condition: service_healthy
```

Compose 会等待 MySQL 和 Redis 健康后再启动后端。这能处理基本启动顺序，但不能替代应用自身的重试、数据库迁移检查或业务健康检查。

本地后端的三个数据目录分别挂载命名卷：

| 卷 | 容器目录 | 用途 | 读写模式 |
| --- | --- | --- | --- |
| `upload-work` | `/data/picture-upload-work` | 分片和导入过程工作目录 | 读写 |
| `pictures` | `/data/pictures` | 新图片目录 | 读写 |
| `legacy-pictures` | `/data/corpusImages` | 历史图片目录模拟 | 只读 |

### 3.4 网络和卷声明

文件末尾声明：

```yaml
networks:
  application:
    driver: bridge

volumes:
  mysql-data:
  redis-data:
  upload-work:
  pictures:
  legacy-pictures:
```

`bridge` 网络让容器可以通过服务名互相访问，同时与宿主机其他网络空间隔离。

顶层 `volumes` 只是声明命名卷；真正把哪个卷挂到哪个目录，是在各服务的 `volumes` 中定义的。

## 4. `compose.prod.yaml`：生产后端覆盖

### 4.1 为什么没有 MySQL 和 Redis 服务

生产服务器已经有包含重要数据的 MySQL 和 Redis。本文件只定义 `backend` 和按需 `maintenance`，不会创建或迁移数据库。

下面的只读命令可以验证最终正常服务列表：

```bash
docker compose --env-file /etc/picture-zip-upload/production.env \
  -f compose.yaml -f compose.prod.yaml \
  config --services
```

不启用 maintenance profile 时，输出应只有：

```text
backend
```

### 4.2 host network

```yaml
network_mode: host
```

在目标 Ubuntu 服务器上，host network 让后端容器直接使用宿主机网络命名空间。因此容器内访问 `127.0.0.1:3306`，就是访问宿主机现有 MySQL。

这种模式下不需要 `ports` 映射。Spring Boot 被限制为：

```yaml
SERVER_ADDRESS: 127.0.0.1
SERVER_PORT: ${BACKEND_PORT:-18080}
```

后端只监听宿主机回环地址，外部请求应先进入宿主机 Nginx，再由 Nginx 转发到 `127.0.0.1:18080`。

这个生产设计以 Linux/Ubuntu 为目标。不要根据 Docker Desktop 上 host network 的表现推断生产服务器行为。

### 4.3 必填的数据库配置

```yaml
MYSQL_URL: ${MYSQL_URL:?MYSQL_URL must point to the existing host MySQL}
MYSQL_USERNAME: ${MYSQL_USERNAME:?MYSQL_USERNAME is required}
MYSQL_PASSWORD: ${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}
```

这里没有开发默认值。生产变量缺失时必须停止，而不是意外连接到错误数据库。

Redis 默认连接宿主机 `127.0.0.1:6379`，密码允许为空，是为了兼容现有服务器是否启用密码的实际情况；真实值仍应明确写在工作区外的生产环境文件中。

### 4.4 绑定挂载真实目录

```yaml
volumes:
  - ${PICTURE_WORK_HOST_PATH:?required}:/data/picture-upload-work
  - ${PICTURE_IMAGE_HOST_PATH:?required}:/data/pictures
  - ${PICTURE_LEGACY_HOST_PATH:?required}:/data/corpusImages:ro
```

绑定挂载的左侧是宿主机真实路径，右侧是容器中的固定路径：

```text
/data/pictures（宿主机） → /data/pictures（容器）
```

这里两侧恰好使用相同路径，便于运维理解，但它们仍属于两个不同文件系统视角。

旧图片目录带 `:ro`，容器不能修改。工作目录和新图片目录可写，但前提是宿主机上的 UID/GID 权限允许容器用户 `10001:10001` 写入。

### 4.5 资源限制

```yaml
mem_limit: ${BACKEND_MEMORY_LIMIT:-2g}
cpus: ${BACKEND_CPUS:-2.0}
pids_limit: ${BACKEND_PIDS_LIMIT:-256}
```

- `mem_limit`：限制容器可使用的内存。
- `cpus`：限制可使用的 CPU 配额，`2.0` 约等于两个 CPU 核心的计算时间。
- `pids_limit`：限制容器中的进程/线程 ID 数量，降低失控创建进程的风险。

JVM 的 `MaxRAMPercentage=75.0` 会基于容器可见内存规划堆，但 Java 进程还有非堆内存，所以不能简单理解成“2 GiB 限制就有 1.5 GiB 业务对象空间”。生产仍需观察实际内存、线程数和 OOM 日志。

## 5. 三个文件合并后的结果

### 本地正常启动

| 服务 | 来源 | 是否默认启动 |
| --- | --- | --- |
| `backend` | 公共定义 + local 补充 | 是 |
| `mysql` | local | 是 |
| `redis` | local | 是 |
| `maintenance` | 公共定义 + local 补充 | 否，仅 profile |

### 生产正常启动

| 服务 | 来源 | 是否默认启动 |
| --- | --- | --- |
| `backend` | 公共定义 + prod 补充 | 是 |
| `maintenance` | 公共定义 + prod 补充 | 否，仅 profile |
| `mysql` | 不存在 | 否，使用宿主机服务 |
| `redis` | 不存在 | 否，使用宿主机服务 |

## 6. 修改 Compose 后怎样自检

先不要启动，依次执行：

```bash
# 检查本地配置能否成功渲染
docker compose --env-file .env.example \
  -f compose.yaml -f compose.local.yaml \
  config --quiet

# 查看本地默认服务
docker compose --env-file .env.example \
  -f compose.yaml -f compose.local.yaml \
  config --services

# 检查生产结构；示例变量只能用于渲染，不能用于真实部署
docker compose --env-file .env.prod.example \
  -f compose.yaml -f compose.prod.yaml \
  config --quiet

docker compose --env-file .env.prod.example \
  -f compose.yaml -f compose.prod.yaml \
  config --services
```

还可以把完整 `config` 输出保存到临时位置进行比较，重点检查：

- 生产是否意外出现 `mysql` 或 `redis` 服务。
- 数据库密码是否被错误地替换为空。
- 本地后端是否仍连接 `mysql:3306`、`redis:6379`。
- 生产后端是否仍只监听 `127.0.0.1`。
- 历史图片目录是否仍为只读。
- 修改的是构建参数还是容器运行环境变量。
