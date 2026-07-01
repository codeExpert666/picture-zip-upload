# 图片压缩包上传后端样例

这个项目是一个 Java 17 + Spring Boot + MyBatis + Redis + MySQL 5.7 的图片压缩包上传样例，面向 T 级 zip 包上传场景。

## 能力范围

- 分片上传大压缩包。
- 服务端按分片序号合并 zip。
- 后台异步、流式解压 zip，避免把压缩包或图片整体读入内存。
- 只处理 zip 根目录下的图片文件。
- 按图片内容 `SHA-256` 判重，图片名不同但内容相同只保留一份物理文件。
- 新图片插入 `corpus_analysis_picture`，默认 `status = MARK`。
- 重复图片不重复存储，不新增记录，只更新 `filename`、`extname`、`update_time`、`upload_id`、`original_zip_name`。
- Redis 或内存记录上传进度。
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
```

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

完成上传并开始后台导入：

```http
POST /api/picture-zip/uploads/{uploadId}/complete
```

查询进度：

```http
GET /api/picture-zip/uploads/{uploadId}
```

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
