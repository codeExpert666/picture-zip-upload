# 图片压缩包上传后端样例

这个项目是一个 Java 17 + Spring Boot + MyBatis + Redis + MySQL 5.7 的图片压缩包上传样例，面向 T 级 zip 包上传场景。

## 能力范围

- 分片上传大压缩包。
- 支持分片 `MD5` 或 `SHA-256` 校验，避免传输损坏的分片落盘。
- 服务端按分片序号合并 zip。
- 后台异步、流式解压 zip，避免把压缩包或图片整体读入内存。
- 只处理 zip 根目录下的图片文件。
- 按图片内容 `SHA-256` 判重，图片名不同但内容相同只保留一份物理文件。
- 新图片插入 `corpus_analysis_picture`，默认 `status = MARK`。
- 重复图片不重复存储，不新增记录，只更新 `filename`、`extname`、`update_time`、`upload_id`、`original_zip_name`。
- Redis 或内存记录上传与后台导入进度，包含导入总文件数 `totalFiles`。
- 通过 `/api/pictures/files/**` 提供图片访问。

## 接口

创建上传任务：

```http
POST /api/picture-zip/uploads
Content-Type: application/json

{
  "originalFilename": "dataset.zip",
  "totalChunks": 1024,
  "totalSize": 1099511627776
}
```

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

## 数据库

执行：

```bash
mysql -h127.0.0.1 -uroot -p ai_dataset < db/schema.sql
```

`content_sha256` 是判重唯一索引。并发上传同一张图片时，数据库唯一索引是最终一致性防线。

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
