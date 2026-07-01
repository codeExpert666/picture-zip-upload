# 图片压缩包上传后端样例

这个项目是一个 Java 17 + Spring Boot + MyBatis + Redis + MySQL 5.7 的图片压缩包上传样例，面向 T 级 zip 包上传场景。

## 能力范围

- 分片上传大压缩包。
- 支持分片 `MD5` 或 `SHA-256` 校验，避免传输损坏的分片落盘。
- 服务端按分片序号合并 zip。
- 后台异步、流式解压 zip，避免把压缩包或图片整体读入内存。
- 支持 zip 内安全相对目录中的图片文件。
- 按图片内容 `SHA-256` 判重，图片名不同但内容相同只保留一份物理文件。
- 新图片按 `businessArea` 写入对应的 `xxx_corpus_analysis_picture`，默认 `status = MARK`。
- 重复图片不重复存储，不新增记录，只更新 `filename`、`extname`、`update_time`、`upload_id`、`original_zip_name`、`operator`。
- Redis 或内存记录上传与后台导入进度，包含导入总文件数 `totalFiles`。
- 通过 `/api/pictures/files/**` 提供接口上传图片访问，可配置额外静态目录访问直接上传图片。

## 接口

创建上传任务：

```http
POST /api/picture-zip/uploads
Content-Type: application/json

{
  "originalFilename": "dataset.zip",
  "totalChunks": 1024,
  "totalSize": 1099511627776,
  "businessArea": "medical",
  "operator": "alice"
}
```

`businessArea` 必须是后端 `picture-upload.business-area-tables` 中配置过的业务领域编码；后端会用该编码解析物理表名。`operator` 表示最近一次上传责任人，新图插入和重复图更新都会写入该字段。

上传分片：

```http
PUT /api/picture-zip/uploads/{uploadId}/chunks/{chunkIndex}
Content-Type: multipart/form-data

file=@chunk.bin
checksumAlgorithm=SHA-256
checksum=<当前分片 SHA-256 十六进制摘要>
```

`checksumAlgorithm` 和 `checksum` 可同时省略以兼容旧客户端；只要传其中一个，就必须两个都传。支持的算法为 `MD5`、`SHA-256`，校验值大小写不敏感。校验失败会返回 `400`，本次临时分片会被删除，已成功上传的同序号分片不会被覆盖。

查询已上传分片列表，用于断点续传：

```http
GET /api/picture-zip/uploads/{uploadId}/chunks
```

```json
{
  "uploadId": "uuid",
  "originalFilename": "dataset.zip",
  "status": "UPLOADING",
  "totalChunks": 1024,
  "uploadedChunks": 2,
  "uploadedChunkIndexes": [0, 2]
}
```

取消未完成上传任务并清理分片：

```http
DELETE /api/picture-zip/uploads/{uploadId}
```

成功时返回 `204 No Content`。仅支持取消 `CREATED`、`UPLOADING`、`FAILED` 状态的任务；任务进入 `MERGING`、`PROCESSING`、`DONE` 后不能取消。取消成功后会删除 `chunks/{uploadId}` 目录和进度记录，再查询该 `uploadId` 会返回任务不存在。

完成上传并开始后台导入：

```http
POST /api/picture-zip/uploads/{uploadId}/complete
```

查询进度：

```http
GET /api/picture-zip/uploads/{uploadId}
```

```json
{
  "uploadId": "uuid",
  "originalFilename": "dataset.zip",
  "status": "PROCESSING",
  "totalChunks": 1024,
  "uploadedChunks": 1024,
  "totalFiles": 14000,
  "processedFiles": 12000,
  "inserted": 10000,
  "duplicated": 1800,
  "failed": 200,
  "message": null,
  "createdAt": "2026-07-01T11:00:00",
  "updatedAt": "2026-07-01T11:10:00"
}
```

后台导入百分比可用 `processedFiles / totalFiles` 计算；`totalFiles` 统计 zip 内非目录条目，目录不参与导入进度。

## 存储目录

默认根目录为 `/tmp/picture-upload`，可通过环境变量调整：

```bash
export PICTURE_UPLOAD_ROOT=/data/picture-upload
```

目录结构：

```text
/data/picture-upload/
  chunks/{uploadId}/chunk-000000.part
  zips/{uploadId}.zip
  images/{sha256前2位}/{sha256}.{ext}
  tmp/{uploadId}/...
```

额外静态目录可用于零复制访问数据组直接上传的图片：

```yaml
picture-upload:
  extra-static-locations:
    direct:
      root-path: /data/pictures
      public-url-prefix: /api/pictures/direct
```

例如 `/data/pictures/病理 图像/第一批/图片 001.png` 的 `file_URL` 应保存为按路径段 UTF-8 编码后的 URL：

```text
/api/pictures/direct/%E7%97%85%E7%90%86%20%E5%9B%BE%E5%83%8F/%E7%AC%AC%E4%B8%80%E6%89%B9/%E5%9B%BE%E7%89%87%20001.png
```

## 数据库

执行：

```bash
mysql -h127.0.0.1 -uroot -p ai_dataset < db/schema.sql
```

每个业务领域一张图片表，表名由 `picture-upload.business-area-tables` 白名单配置控制，例如：

```yaml
picture-upload:
  business-area-tables:
    medical: medical_corpus_analysis_picture
```

`content_sha256` 是单张业务表内的判重唯一索引。并发上传同一张图片时，数据库唯一索引是最终一致性防线。历史表迁移时先执行 `db/picture-maintenance-migration.sql` 中的字段扩容和新增字段语句，完成旧记录回填并处理冲突后，再添加 `uk_picture_sha256` 唯一索引。

## 维护脚本

历史表尚未新增字段时，推荐顺序：

1. 备份目标业务表。
2. 执行 `db/picture-maintenance-migration.sql` 的字段扩容和新增可空字段部分。
3. 部署包含额外静态目录配置的应用。
4. dry-run 旧记录回填脚本。
5. 正式执行旧记录回填脚本。
6. 处理内容哈希冲突报告。
7. 添加 `content_sha256` 唯一索引。
8. dry-run 新目录导入脚本。
9. 正式执行新目录导入脚本。
10. 抽样验证旧图、新图、中文路径 URL。

旧记录回填优先使用表内 `file_path`，缺失时再用 `file_URL` 结合静态映射反推本地路径：

```bash
scripts/backfill-existing-picture-records.sh \
  --business-area medical \
  --operator data-team \
  --batch-id legacy-backfill-20260701 \
  --dry-run true
```

新目录导入会递归扫描 `/data/pictures`，不复制、不移动图片，只写入原始 `file_path` 和编码后的 `file_URL`：

```bash
scripts/import-direct-picture-directory.sh \
  --business-area medical \
  --source-root /data/pictures \
  --public-url-prefix /api/pictures/direct \
  --operator data-team \
  --batch-id direct-import-20260701 \
  --dry-run true
```

## Redis 进度

本地默认使用内存进度，便于直接运行。公司环境启用 Redis：

```bash
export PICTURE_UPLOAD_PROGRESS_STORE=redis
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
```

## 运行

```bash
mvn test
mvn spring-boot:run
```

如果本机没有 MySQL，应用启动时会因为数据源不可用失败。可以先启动 MySQL 并执行 `db/schema.sql`。
