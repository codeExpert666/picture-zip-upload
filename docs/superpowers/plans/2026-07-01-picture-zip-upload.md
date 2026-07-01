# Picture Zip Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a complete Spring Boot backend sample for T-level image zip upload, server-side storage, SHA-256 deduplication, and MyBatis persistence.

**Architecture:** Use chunk upload for large zip files, stream-merge chunks to one zip, then asynchronously stream zip entries into temp files while computing SHA-256. Persist new images once and update existing rows for duplicate content.

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis, Redis, MySQL 5.7, Apache Commons Compress, JUnit 5.

---

### Task 1: Core Behavior Tests

**Files:**
- Create: `src/test/java/com/example/picturezipupload/ImageTypeDetectorTest.java`
- Create: `src/test/java/com/example/picturezipupload/ZipEntryNameValidatorTest.java`
- Create: `src/test/java/com/example/picturezipupload/FileUploadStorageServiceTest.java`
- Create: `src/test/java/com/example/picturezipupload/ZipPictureImportServiceTest.java`

- [x] Write failing tests for image magic validation, root-only zip entry validation, chunk merge order, and duplicate-content import.
- [x] Run `mvn test` and verify it fails because production classes are missing.

### Task 2: Core Implementation

**Files:**
- Create: `src/main/java/com/example/picturezipupload/importing/*`
- Create: `src/main/java/com/example/picturezipupload/storage/FileUploadStorageService.java`
- Create: `src/main/java/com/example/picturezipupload/domain/*`
- Create: `src/main/java/com/example/picturezipupload/progress/*`

- [x] Implement zip entry path validation.
- [x] Implement image extension and magic header validation.
- [x] Implement chunk persistence and merge.
- [x] Implement stream-based zip import and SHA-256 deduplication.
- [x] Run `mvn test` and verify tests pass.

### Task 3: Backend Integration

**Files:**
- Create: `src/main/java/com/example/picturezipupload/controller/PictureZipUploadController.java`
- Create: `src/main/java/com/example/picturezipupload/service/PictureZipUploadService.java`
- Create: `src/main/java/com/example/picturezipupload/mapper/CorpusAnalysisPictureMapper.java`
- Create: `src/main/java/com/example/picturezipupload/repository/MyBatisPictureRecordRepository.java`
- Create: `src/main/java/com/example/picturezipupload/config/*`
- Create: `src/main/java/com/example/picturezipupload/web/*`

- [x] Implement create/upload-chunk/complete/progress endpoints.
- [x] Implement async import dispatch.
- [x] Implement MyBatis persistence adapter.
- [x] Implement static image URL mapping.

### Task 4: Operational Artifacts

**Files:**
- Create: `src/main/resources/application.yml`
- Create: `db/schema.sql`
- Create: `README.md`

- [x] Add runtime configuration.
- [x] Add MySQL table schema and deduplication indexes.
- [x] Document upload flow, storage layout, Redis mode, and run commands.

### Task 5: Final Verification

**Files:**
- All project files.

- [x] Run `mvn test`.
- [x] Run `mvn package`.
- [x] Inspect `git status --short`.
