# 图片维护脚本使用指南

本文档单独说明本项目中两份图片维护脚本的用途、执行顺序、参数和注意事项：

- `scripts/backfill-existing-picture-records.sh`
- `scripts/import-direct-picture-directory.sh`

这两个脚本用于一次性或少量批次的生产维护任务，不替代正式的图片压缩包上传接口。脚本默认通过 Spring Boot 启动应用上下文和 MyBatis，但不会打开 HTTP 服务端口。

## 适用场景

当前业务中存在两类需要维护的数据：

1. 历史已上传图片：文件已经在旧静态资源目录下，数据库中也有记录，但表结构新增字段尚未回填。
2. 数据组临时直接上传的新图片：图片文件已经被直接放到服务器目录，例如 `/data/pictures`，但业务图片表中还没有记录。

本次方案采用零复制处理：

- 不复制图片。
- 不移动图片。
- 不创建软链接或硬链接。
- 数据库 `file_path` 指向真实本地路径。
- 数据库 `file_URL` 指向 Spring 静态资源映射 URL。

## 脚本清单

### 旧记录回填脚本

```bash
scripts/backfill-existing-picture-records.sh
```

用途：处理已经有数据库记录的历史图片，回填新增字段，例如 `content_sha256`、`file_size`、`upload_id`、`operator` 等。

该脚本不插入新记录，不修改标注状态，不默认覆盖历史 `file_path` 或 `file_URL`。

### 新目录导入脚本

```bash
scripts/import-direct-picture-directory.sh
```

用途：递归扫描一个服务器目录，例如 `/data/pictures`，把尚未入库的图片按原地引用方式插入业务图片表。

该脚本会校验图片扩展名和图片魔数，计算 SHA-256，按 `content_sha256` 判重。

## 上线前准备

### 1. 备份业务表

执行任何 DDL 或正式脚本前，先备份目标业务表。

macOS/Linux/Git Bash 示例：

```bash
backup_file="medical_corpus_analysis_picture_$(date +%Y%m%d_%H%M%S).sql"
mysqldump -h127.0.0.1 -uroot -p --default-character-set=utf8mb4 \
  ai_dataset medical_corpus_analysis_picture \
  --result-file="${backup_file}"
```

Windows PowerShell 示例：

```powershell
$mysqldump = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
$backupFile = "medical_corpus_analysis_picture_$(Get-Date -Format 'yyyyMMdd_HHmmss').sql"
& $mysqldump -h127.0.0.1 -uroot -p --default-character-set=utf8mb4 ai_dataset medical_corpus_analysis_picture --result-file="$backupFile"
```

Windows CMD 示例：

```bat
for /f %i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set BACKUP_FILE=medical_corpus_analysis_picture_%i.sql
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" -h127.0.0.1 -uroot -p --default-character-set=utf8mb4 ai_dataset medical_corpus_analysis_picture --result-file="%BACKUP_FILE%"
```

如果 `mysqldump` 已经加入 `PATH`，Windows 示例中的完整路径可以直接改成 `mysqldump`；如果实际安装目录不是 MySQL Server 8.0，按本机路径调整。CMD 示例如果写入 `.bat` 文件，`%i` 需要改成 `%%i`。PowerShell 下不要用 `mysqldump ... > backup.sql` 生成备份文件，避免导出的 SQL 文件编码不适合后续恢复。

### 2. 执行字段迁移

历史公司表如果尚未新增字段，应先执行 `db/picture-maintenance-migration.sql` 中的字段扩容和新增字段语句。

建议分两步执行：

1. 先执行 `MODIFY file_URL/file_path` 和 `ADD COLUMN ...`。
2. 等旧记录回填完成、冲突处理完成后，再添加 `uk_picture_sha256` 唯一索引。

迁移原因：

- `file_URL` 和 `file_path` 需要支持多级目录和中文路径，建议扩到 `varchar(1024)`。
- `content_sha256` 初期应允许为空，避免历史数据未回填前无法上线。
- 唯一索引应在回填冲突处理后再加，避免历史重复图片直接阻塞迁移。

### 3. 配置静态资源目录

旧图片和后续接口上传图片继续使用原静态目录，例如：

```text
/api/pictures/files/** -> /data/picture-upload/images/**
```

数据组直接上传的新目录使用额外静态目录，例如：

```text
/api/pictures/direct/** -> /data/pictures/**
```

示例配置：

```yaml
picture-upload:
  root-path: /data/picture-upload
  public-url-prefix: /api/pictures/files
  business-area-tables:
    medical: medical_corpus_analysis_picture
  extra-static-locations:
    direct:
      root-path: /data/pictures
      public-url-prefix: /api/pictures/direct
```

### 4. 确认应用连接配置

脚本启动的是同一个 Spring Boot 应用，需要能连接生产数据库。常用环境变量示例：

```bash
export MYSQL_URL='jdbc:mysql://127.0.0.1:3306/ai_dataset?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME='root'
export MYSQL_PASSWORD='your-password'
export PICTURE_UPLOAD_ROOT='/data/picture-upload'
export PICTURE_DIRECT_ROOT='/data/pictures'
export PICTURE_DIRECT_PUBLIC_URL_PREFIX='/api/pictures/direct'
```

如果需要指定已有 jar，可以设置：

```bash
export APP_JAR=/path/to/picture-zip-upload-0.0.1-SNAPSHOT.jar
```

如果 `APP_JAR` 不存在，脚本会在项目根目录执行：

```bash
mvn -DskipTests package
```

## 推荐执行顺序

严格建议按以下顺序执行：

1. 备份目标业务表。
2. 执行字段迁移，先不要添加唯一索引。
3. 部署包含额外静态目录配置的应用版本。
4. dry-run 执行旧记录回填脚本。
5. 确认旧记录 dry-run 报告。
6. 正式执行旧记录回填脚本。
7. 处理内容哈希冲突。
8. 添加 `content_sha256` 唯一索引。
9. dry-run 执行新目录导入脚本。
10. 确认新目录 dry-run 报告。
11. 正式执行新目录导入脚本。
12. 抽样验证旧图、新图、中文路径图片 URL。

先回填旧记录，再导入新目录，是为了让新目录导入时能通过 `content_sha256` 正确识别历史重复图片，避免插入重复业务记录。

## 旧记录回填脚本

### 基本命令

dry-run：

```bash
scripts/backfill-existing-picture-records.sh \
  --business-area medical \
  --operator data-team \
  --batch-id legacy-backfill-20260702 \
  --dry-run true
```

正式执行：

```bash
scripts/backfill-existing-picture-records.sh \
  --business-area medical \
  --operator data-team \
  --batch-id legacy-backfill-20260702 \
  --dry-run false
```

带历史目录兜底：

```bash
scripts/backfill-existing-picture-records.sh \
  --business-area medical \
  --legacy-root /data/picture-upload/images \
  --operator data-team \
  --batch-id legacy-backfill-20260702 \
  --limit 1000 \
  --dry-run true
```

### 参数说明

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `--business-area` | 是 | 业务领域编码，会通过 `picture-upload.business-area-tables` 解析到具体图片表 |
| `--operator` | 否 | 操作人，写入 `operator` 字段 |
| `--batch-id` | 是 | 本次维护批次号，写入 `upload_id` 字段 |
| `--legacy-root` | 否 | 当 `file_path` 和 `file_URL` 都无法定位文件时，按 `filename.extname` 兜底查找的目录 |
| `--limit` | 否 | 单次最多处理的旧记录数量，默认 `1000` |
| `--dry-run` | 否 | 是否只统计不写库，默认应用配置为 `true` |

### 文件定位顺序

旧记录回填按以下顺序定位本地文件：

1. 优先使用数据库已有 `file_path`。
2. 如果 `file_path` 不可用，尝试用 `file_URL` 结合静态资源映射反解本地路径。
3. 如果仍不可用，且传入了 `--legacy-root`，则在该目录下按 `filename + extname` 查找。

第三种兜底只有唯一匹配时才会使用。如果同名文件有多个，脚本会跳过该记录，避免误回填。

### 回填字段

正式执行时会回填：

- `content_sha256`
- `file_size`
- `upload_id`
- `original_zip_name`
- `operator`
- `update_time`

不会修改：

- `status`
- 标注相关字段
- 可靠的历史 `file_path`
- 可靠的历史 `file_URL`
- `import_time`

## 新目录导入脚本

### 基本命令

dry-run：

```bash
scripts/import-direct-picture-directory.sh \
  --business-area medical \
  --source-root /data/pictures \
  --public-url-prefix /api/pictures/direct \
  --operator data-team \
  --batch-id direct-import-20260702 \
  --dry-run true
```

正式执行：

```bash
scripts/import-direct-picture-directory.sh \
  --business-area medical \
  --source-root /data/pictures \
  --public-url-prefix /api/pictures/direct \
  --operator data-team \
  --batch-id direct-import-20260702 \
  --dry-run false
```

### 参数说明

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `--business-area` | 是 | 业务领域编码，会通过白名单解析到具体图片表 |
| `--source-root` | 是 | 数据组直接上传图片所在根目录，例如 `/data/pictures` |
| `--public-url-prefix` | 是 | 对应的静态资源 URL 前缀，例如 `/api/pictures/direct` |
| `--operator` | 否 | 操作人，写入 `operator` 字段 |
| `--batch-id` | 是 | 本次导入批次号，写入 `upload_id` 字段 |
| `--dry-run` | 否 | 是否只统计不写库，默认应用配置为 `true` |

### 入库行为

脚本会递归扫描 `--source-root` 下的普通文件，并执行以下逻辑：

1. 校验扩展名是否支持：`jpg`、`jpeg`、`png`、`webp`、`bmp`、`gif`。
2. 校验图片魔数，避免非图片伪装成图片入库。
3. 流式计算 `content_sha256` 和 `file_size`。
4. 按 `content_sha256` 查询业务图片表。
5. 如果已存在相同内容图片，计入 `duplicated`，不插入新记录。
6. 如果不存在，插入新记录。

新记录字段规则：

| 字段 | 写入规则 |
| --- | --- |
| `voice_code` | 新 UUID |
| `filename` | 文件最后一级名称，不含扩展名，最多 100 字符 |
| `extname` | 小写规范化扩展名 |
| `file_path` | 原始本地绝对路径 |
| `file_URL` | 按静态前缀和相对路径生成的 URL |
| `content_sha256` | 文件内容 SHA-256 |
| `file_size` | 文件字节数 |
| `status` | `MARK` |
| `upload_id` | `--batch-id` |
| `original_zip_name` | `DIRECT:{source-root}` |
| `operator` | `--operator` |

## 中文路径和 URL 编码

`file_path` 保存文件系统真实路径，可以包含中文、空格和多级目录：

```text
/data/pictures/病理 图像/第一批/图片 001.png
```

`file_URL` 保存 URL path 编码后的路径：

```text
/api/pictures/direct/%E7%97%85%E7%90%86%20%E5%9B%BE%E5%83%8F/%E7%AC%AC%E4%B8%80%E6%89%B9/%E5%9B%BE%E7%89%87%20001.png
```

编码规则：

- 只编码每个 path segment。
- 不编码路径分隔符 `/`。
- 空格编码为 `%20`。
- 不使用表单参数风格的 `+`。

脚本会拒绝危险相对路径，例如：

- `..`
- 绝对路径逃逸
- 反斜杠路径
- 空路径段

## 报告字段说明

脚本结束后会在日志中输出 `PictureMaintenanceReport`，字段含义如下：

| 字段 | 含义 |
| --- | --- |
| `scanned` | 扫描或读取的文件/记录数量 |
| `inserted` | 新目录导入中可插入或已插入的记录数量 |
| `duplicated` | 内容哈希已存在，跳过的图片数量 |
| `backfilled` | 旧记录回填中可回填或已回填的记录数量 |
| `invalid` | 扩展名不支持、魔数不匹配或不是有效图片的数量 |
| `missing` | 旧记录无法定位到本地文件的数量 |
| `conflicted` | 旧记录计算出的哈希与其他记录冲突的数量 |

dry-run 模式下，`inserted` 和 `backfilled` 表示如果正式执行会处理的数量，不代表已经写库。

## 执行后校验

### 检查旧记录回填情况

```sql
SELECT COUNT(*) AS missing_metadata
FROM medical_corpus_analysis_picture
WHERE content_sha256 IS NULL
   OR file_size IS NULL
   OR file_size = 0;
```

### 检查内容哈希重复

```sql
SELECT content_sha256, COUNT(*) AS cnt
FROM medical_corpus_analysis_picture
WHERE content_sha256 IS NOT NULL
GROUP BY content_sha256
HAVING COUNT(*) > 1;
```

### 检查新目录导入批次

```sql
SELECT COUNT(*) AS imported
FROM medical_corpus_analysis_picture
WHERE upload_id = 'direct-import-20260702';
```

### 抽样检查 URL

```sql
SELECT voice_code, filename, file_URL, file_path
FROM medical_corpus_analysis_picture
WHERE upload_id = 'direct-import-20260702'
LIMIT 20;
```

拿 `file_URL` 到浏览器或用 `curl` 抽样访问：

```bash
curl -I 'http://server-host/api/pictures/direct/%E7%97%85%E7%90%86%20%E5%9B%BE%E5%83%8F/%E5%9B%BE%E7%89%87%20001.png'
```

## 常见问题

### 脚本启动后连接数据库失败

检查 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`，并确认脚本运行机器能访问 MySQL。

### `file_URL` 访问 404

检查：

1. `picture-upload.extra-static-locations` 是否配置了对应 URL 前缀。
2. `root-path` 是否指向真实图片目录。
3. 数据库中的 `file_URL` 是否使用了正确的 `public-url-prefix`。
4. 中文和空格是否按 URL path 编码。

### dry-run 数量和正式执行数量不同

正式执行期间如果其他任务也在写入图片表，可能出现并发重复。脚本会依赖数据库唯一索引兜底，并把冲突计入 `duplicated` 或 `conflicted`。

### 旧记录出现 `conflicted`

说明该旧记录计算出的 `content_sha256` 已经属于另一条记录。不要自动合并，建议人工确认：

- 是否确实是同一张图片被重复登记。
- 两条记录是否都有标注数据。
- 哪条记录应保留为主记录。

### 旧记录出现 `missing`

说明脚本无法通过 `file_path`、`file_URL` 或 `legacy-root + filename/extname` 定位文件。建议人工检查：

- 历史 `file_path` 是否过期。
- 静态资源目录是否迁移过。
- 文件是否被删除。
- `filename` 和 `extname` 是否与真实文件名一致。

## 回滚建议

如果只执行了 dry-run，不需要回滚。

如果正式执行了旧记录回填，可以按 `upload_id = batch-id` 定位本批次更新记录，再根据备份恢复新增字段。

如果正式执行了新目录导入，可以按批次删除本次新增记录：

```sql
DELETE FROM medical_corpus_analysis_picture
WHERE upload_id = 'direct-import-20260702';
```

删除前应确认这些记录尚未进入后续标注或业务流程。

## 安全边界

- 脚本不根据用户输入直接拼接表名，业务领域必须通过后端白名单解析。
- 脚本不复制大图片，避免额外占用服务器磁盘。
- 脚本不修改旧记录标注状态。
- 脚本默认 dry-run，正式执行必须显式设置 `--dry-run false`。
- 新目录导入只引用 `--source-root` 下的安全相对路径。
