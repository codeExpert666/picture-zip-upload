# Vue3 图片压缩包上传前端实现说明

本文档基于当前后端实现，说明 Vue3 + JavaScript 前端如何接入图片压缩包上传功能。

## 1. 后端接口契约

当前后端采用“创建任务 -> 查询已上传分片 -> 分片上传 -> 完成合并 -> 轮询进度”的流程。

### 1.1 创建上传任务

```http
POST /api/picture-zip/uploads
Content-Type: application/json
```

请求体：

```json
{
  "originalFilename": "dataset.zip",
  "totalChunks": 1024,
  "totalSize": 1099511627776
}
```

响应：

```json
{
  "uploadId": "uuid",
  "status": "CREATED"
}
```

### 1.2 上传单个分片

```http
PUT /api/picture-zip/uploads/{uploadId}/chunks/{chunkIndex}
Content-Type: multipart/form-data
```

表单字段：

```text
file=@chunk.bin
checksumAlgorithm=SHA-256
checksum=<当前分片 SHA-256 十六进制摘要>
```

说明：

- `chunkIndex` 从 `0` 开始。
- 后端按分片序号顺序合并 zip。
- `checksumAlgorithm` 支持 `MD5`、`SHA-256`；推荐前端使用浏览器原生支持的 `SHA-256`。
- `checksumAlgorithm` 和 `checksum` 可同时省略以兼容旧客户端；如果只传其中一个，后端会返回 `400`。
- 分片校验失败时后端返回 `400`，并删除本次临时分片，不覆盖同序号已成功上传的分片。
- 当前后端默认单个 multipart 请求上限为 `128MB`，前端分片大小必须小于该值。

### 1.3 查询已上传分片列表

```http
GET /api/picture-zip/uploads/{uploadId}/chunks
```

响应字段：

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

说明：

- `uploadedChunkIndexes` 是已完整落盘的分片序号列表，前端断点续传时应以它为准。
- `uploadedChunks` 是 `uploadedChunkIndexes.length`，只适合展示数量，不适合作为恢复依据。
- 后端会忽略上传中断遗留的临时分片文件。
- `complete` 合并后会清理分片目录，此接口主要用于任务进入合并前的恢复；若 `status` 已是 `MERGING`、`PROCESSING`、`DONE` 或 `FAILED`，前端不应继续补传分片。

### 1.4 完成上传

```http
POST /api/picture-zip/uploads/{uploadId}/complete
```

调用后，后端会合并分片并异步导入 zip。

### 1.5 查询任务进度

```http
GET /api/picture-zip/uploads/{uploadId}
```

响应字段：

```json
{
  "uploadId": "uuid",
  "originalFilename": "dataset.zip",
  "status": "PROCESSING",
  "totalChunks": 1024,
  "uploadedChunks": 1024,
  "processedFiles": 12000,
  "inserted": 10000,
  "duplicated": 1800,
  "failed": 200,
  "message": null,
  "createdAt": "2026-07-01T11:00:00",
  "updatedAt": "2026-07-01T11:10:00"
}
```

状态枚举：

| 状态 | 含义 |
| --- | --- |
| `CREATED` | 任务已创建，尚未上传分片 |
| `UPLOADING` | 正在接收分片 |
| `MERGING` | 分片已收齐，正在合并 zip |
| `PROCESSING` | zip 已合并，正在后台解压、判重和入库 |
| `DONE` | 后台导入完成 |
| `FAILED` | 上传或导入失败 |

## 2. 前端实现策略

建议前端分为两个进度阶段：

1. **本地上传阶段**：根据每个分片的 `onUploadProgress` 计算上传百分比。
2. **后台处理阶段**：调用 `complete` 后轮询后端进度，展示 `processedFiles`、`inserted`、`duplicated`、`failed`。

当前后端不会提前统计 zip 内图片总数，因此后台处理阶段不建议展示百分比，而是展示“已处理 N 张”。

推荐参数：

```js
const CHUNK_SIZE = 64 * 1024 * 1024
const CONCURRENCY = 3
const POLLING_INTERVAL = 2000
```

原因：

- `64MB` 小于后端默认 `128MB` multipart 限制。
- 并发数控制在 `3`，避免浏览器和网关连接过多。
- 轮询间隔 `2s` 能兼顾实时性和服务端压力。
- `SHA-256` 可直接使用浏览器 Web Crypto API；如必须使用 `MD5`，前端需要引入可靠的 MD5 实现。

## 3. API 封装

建议创建 `src/api/pictureZipUpload.js`。

```js
import axios from 'axios'

const request = axios.create({
  baseURL: '/api'
})

export function createUploadTask({ originalFilename, totalChunks, totalSize }) {
  return request.post('/picture-zip/uploads', {
    originalFilename,
    totalChunks,
    totalSize
  }).then(res => res.data)
}

const CHECKSUM_ALGORITHM = 'SHA-256'

async function calculateChecksum(blob, algorithm = CHECKSUM_ALGORITHM) {
  const buffer = await blob.arrayBuffer()
  const hash = await crypto.subtle.digest(algorithm, buffer)

  return Array.from(new Uint8Array(hash))
    .map(value => value.toString(16).padStart(2, '0'))
    .join('')
}

export async function uploadChunk(uploadId, chunkIndex, blob, onProgress) {
  const checksum = await calculateChecksum(blob)
  const formData = new FormData()
  formData.append('file', blob)
  formData.append('checksumAlgorithm', CHECKSUM_ALGORITHM)
  formData.append('checksum', checksum)

  return request.put(`/picture-zip/uploads/${uploadId}/chunks/${chunkIndex}`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress: onProgress
  }).then(res => res.data)
}

export function completeUpload(uploadId) {
  return request.post(`/picture-zip/uploads/${uploadId}/complete`)
    .then(res => res.data)
}

export function getUploadedChunks(uploadId) {
  return request.get(`/picture-zip/uploads/${uploadId}/chunks`)
    .then(res => res.data)
}

export function getUploadProgress(uploadId) {
  return request.get(`/picture-zip/uploads/${uploadId}`)
    .then(res => res.data)
}
```

## 4. Vue3 组件核心代码

示例组件可命名为 `PictureZipUploader.vue`。

```vue
<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import {
  createUploadTask,
  uploadChunk,
  completeUpload,
  getUploadedChunks,
  getUploadProgress
} from '@/api/pictureZipUpload'

const CHUNK_SIZE = 64 * 1024 * 1024
const CONCURRENCY = 3
const POLLING_INTERVAL = 2000

const file = ref(null)
const uploadId = ref('')
const uploading = ref(false)
const completing = ref(false)
const serverProgress = ref(null)
const pollingTimer = ref(null)

const chunkProgress = reactive(new Map())

const uploadPercent = computed(() => {
  if (!file.value) {
    return 0
  }

  let uploaded = 0
  for (const value of chunkProgress.values()) {
    uploaded += value
  }

  return Math.min(100, Math.floor((uploaded / file.value.size) * 100))
})

function handleFileChange(event) {
  const selected = event.target.files?.[0]
  if (!selected) {
    return
  }

  if (!selected.name.toLowerCase().endsWith('.zip')) {
    window.alert('只支持上传 zip 压缩包')
    event.target.value = ''
    return
  }

  file.value = selected
  uploadId.value = ''
  serverProgress.value = null
  chunkProgress.clear()
}

function createChunks(selectedFile) {
  const chunks = []
  const totalChunks = Math.ceil(selectedFile.size / CHUNK_SIZE)

  for (let index = 0; index < totalChunks; index++) {
    const start = index * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, selectedFile.size)

    chunks.push({
      index,
      blob: selectedFile.slice(start, end),
      size: end - start
    })
  }

  return chunks
}

async function runWithConcurrency(tasks, limit) {
  const executing = new Set()

  for (const task of tasks) {
    const promise = task().finally(() => executing.delete(promise))
    executing.add(promise)

    if (executing.size >= limit) {
      await Promise.race(executing)
    }
  }

  await Promise.all(executing)
}

async function startUpload() {
  if (!file.value || uploading.value || completing.value) {
    return
  }

  uploading.value = true

  try {
    const chunks = createChunks(file.value)

    const task = await createUploadTask({
      originalFilename: file.value.name,
      totalChunks: chunks.length,
      totalSize: file.value.size
    })

    uploadId.value = task.uploadId

    const tasks = chunks.map(chunk => async () => {
      await uploadChunk(uploadId.value, chunk.index, chunk.blob, event => {
        chunkProgress.set(chunk.index, event.loaded)
      })

      chunkProgress.set(chunk.index, chunk.size)
    })

    await runWithConcurrency(tasks, CONCURRENCY)

    completing.value = true
    serverProgress.value = await completeUpload(uploadId.value)
    startPolling()
  } catch (error) {
    console.error(error)
    window.alert(error.response?.data?.detail || '上传失败')
  } finally {
    uploading.value = false
    completing.value = false
  }
}

function startPolling() {
  stopPolling()

  pollingTimer.value = window.setInterval(async () => {
    const progress = await getUploadProgress(uploadId.value)
    serverProgress.value = progress

    if (progress.status === 'DONE' || progress.status === 'FAILED') {
      stopPolling()
    }
  }, POLLING_INTERVAL)
}

function stopPolling() {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
}

onBeforeUnmount(stopPolling)
</script>

<template>
  <section class="picture-upload">
    <input type="file" accept=".zip" @change="handleFileChange">

    <button :disabled="!file || uploading || completing" @click="startUpload">
      {{ uploading ? '上传中' : completing ? '提交处理中' : '开始上传' }}
    </button>

    <div v-if="file" class="upload-panel">
      <p>文件名：{{ file.name }}</p>
      <p>文件大小：{{ (file.size / 1024 / 1024 / 1024).toFixed(2) }} GB</p>
      <p>上传进度：{{ uploadPercent }}%</p>
      <progress :value="uploadPercent" max="100" />
    </div>

    <div v-if="serverProgress" class="upload-panel">
      <p>任务状态：{{ serverProgress.status }}</p>
      <p>已处理图片：{{ serverProgress.processedFiles }}</p>
      <p>新增图片：{{ serverProgress.inserted }}</p>
      <p>重复图片：{{ serverProgress.duplicated }}</p>
      <p>失败条目：{{ serverProgress.failed }}</p>
      <p v-if="serverProgress.message">失败原因：{{ serverProgress.message }}</p>
    </div>
  </section>
</template>
```

## 5. 需要注意的边界

### 5.1 不要用单请求上传整个 zip

T 级压缩包不能用一个普通 multipart 请求上传。前端必须切片，后端当前接口也是按分片设计的。

### 5.2 断点续传以 `uploadedChunkIndexes` 为准

页面刷新或上传中断后，如果前端仍保存着 `uploadId` 且用户重新选择的是同一个 zip 文件，可以先调用：

```http
GET /api/picture-zip/uploads/{uploadId}/chunks
```

然后把 `uploadedChunkIndexes` 转成 `Set`，只上传缺失分片。

前端建议：

- 本轮上传进度用浏览器本地分片进度计算。
- 恢复上传时，已上传分片的本地进度直接设置为该分片大小。
- `uploadedChunks` 仅展示数量，是否跳过某个分片必须看 `uploadedChunkIndexes`。
- 本地内存进度模式不适合服务重启后的恢复；生产多实例或需要跨重启恢复时应启用 Redis 进度存储。

### 5.3 后台导入阶段不展示百分比

当前后端没有返回 zip 内总图片数，只返回：

- `processedFiles`
- `inserted`
- `duplicated`
- `failed`

因此后台处理阶段建议展示计数，不展示百分比。

### 5.4 失败处理

前端需要区分两类失败：

- 分片上传失败：停止上传，允许用户重新开始。
- 后台导入失败：轮询结果为 `FAILED`，展示 `message`。

## 6. 后续可增强点

如果产品需要更完整的大文件体验，建议后端后续补充：

1. 取消上传任务接口，用于清理未完成分片。
2. 后台导入 `totalFiles`，用于展示导入百分比。
3. WebSocket 或 SSE 推送进度，替代轮询。
