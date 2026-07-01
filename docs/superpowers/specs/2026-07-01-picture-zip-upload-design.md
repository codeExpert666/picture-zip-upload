# Picture Zip Upload Design

**Goal:** Build a backend sample for uploading T-level image zip packages, streaming images to server storage, deduplicating by image content, and writing business-area picture table records.

**Architecture:** The API uses resumable chunk upload. The server persists chunks, merges them into one zip, and submits an asynchronous import task. The import task streams zip entries one by one, validates that each entry is a root-level image, computes SHA-256 while writing a temporary file, and uses `content_sha256` as the deduplication key.

**Storage:** Physical images are stored under `images/{sha256-prefix}/{sha256}.{ext}`. This makes identical content converge to one path regardless of uploaded filename.

**Database:** Existing table fields remain, and the sample adds `content_sha256`, `file_size`, `upload_id`, and `original_zip_name`. `content_sha256` has a unique index and is the final concurrency guard.

**Duplicate Behavior:** If a picture with the same SHA-256 already exists, no new file is stored and no new row is inserted. The existing row only updates import metadata: `filename`, `extname`, `update_time`, `upload_id`, and `original_zip_name`; annotation state is preserved.

**Progress:** Upload and import progress is stored behind `UploadProgressStore`. The sample supports in-memory progress for local runs and Redis progress for company deployment.

**Error Handling:** Invalid zip paths, nested files, unsupported extensions, and non-image magic headers are counted as failed files and do not stop the whole import. Fatal zip or storage errors mark the task as `FAILED`.
