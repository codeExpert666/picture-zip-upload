# Direct Picture Maintenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a zero-copy maintenance path for existing picture metadata backfill and direct directory imports, while keeping the zip upload path compatible with safe nested directories.

**Architecture:** The application keeps the existing hash-based upload storage and adds configurable extra static resource mappings for direct directories. Two shell scripts call one Spring Boot maintenance runner in different modes: existing-record backfill and direct-directory import. Shared helpers handle safe relative paths, UTF-8 URL path segment encoding/decoding, image inspection, and reports.

**Tech Stack:** Spring Boot 3.3, MyBatis annotations, Java NIO, JUnit 5, AssertJ, Mockito, Bash.

---

### Task 1: Path And Zip Behavior

**Files:**
- Modify: `src/main/java/com/example/picturezipupload/importing/ZipEntryNameValidator.java`
- Modify: `src/main/java/com/example/picturezipupload/importing/ZipPictureImportService.java`
- Create: `src/main/java/com/example/picturezipupload/importing/PictureFileNameUtils.java`
- Create: `src/main/java/com/example/picturezipupload/maintenance/StaticPicturePathResolver.java`
- Test: `src/test/java/com/example/picturezipupload/ZipEntryNameValidatorTest.java`
- Test: `src/test/java/com/example/picturezipupload/ZipPictureImportServiceTest.java`
- Test: `src/test/java/com/example/picturezipupload/StaticPicturePathResolverTest.java`

- [ ] **Step 1: Write failing tests** for safe nested zip entries, basename extraction, and UTF-8 URL path segment encoding/decoding.
- [ ] **Step 2: Run focused tests** with `mvn -Dtest=ZipEntryNameValidatorTest,ZipPictureImportServiceTest,StaticPicturePathResolverTest test` and confirm failures.
- [ ] **Step 3: Implement minimal path helpers and zip import changes**.
- [ ] **Step 4: Re-run focused tests** and confirm pass.

### Task 2: Static Resource Configuration

**Files:**
- Modify: `src/main/java/com/example/picturezipupload/config/PictureUploadProperties.java`
- Modify: `src/main/java/com/example/picturezipupload/web/StaticResourceConfiguration.java`
- Test: `src/test/java/com/example/picturezipupload/StaticResourceConfigurationTest.java`

- [ ] **Step 1: Write failing tests** verifying the default image mapping and an extra direct-directory mapping.
- [ ] **Step 2: Run `mvn -Dtest=StaticResourceConfigurationTest test`** and confirm failures.
- [ ] **Step 3: Add extra static location properties and register mappings**.
- [ ] **Step 4: Re-run focused test** and confirm pass.

### Task 3: Maintenance Runner And Scripts

**Files:**
- Create: `src/main/java/com/example/picturezipupload/maintenance/PictureFileInspector.java`
- Create: `src/main/java/com/example/picturezipupload/maintenance/PictureFileMetadata.java`
- Create: `src/main/java/com/example/picturezipupload/maintenance/PictureMaintenanceMode.java`
- Create: `src/main/java/com/example/picturezipupload/maintenance/PictureMaintenanceProperties.java`
- Create: `src/main/java/com/example/picturezipupload/maintenance/PictureMaintenanceReport.java`
- Create: `src/main/java/com/example/picturezipupload/maintenance/PictureMaintenanceRepository.java`
- Create: `src/main/java/com/example/picturezipupload/maintenance/MyBatisPictureMaintenanceRepository.java`
- Create: `src/main/java/com/example/picturezipupload/maintenance/PictureMaintenanceService.java`
- Create: `src/main/java/com/example/picturezipupload/maintenance/PictureMaintenanceRunner.java`
- Modify: `src/main/java/com/example/picturezipupload/mapper/CorpusAnalysisPictureMapper.java`
- Create: `scripts/backfill-existing-picture-records.sh`
- Create: `scripts/import-direct-picture-directory.sh`
- Test: `src/test/java/com/example/picturezipupload/PictureMaintenanceServiceTest.java`

- [ ] **Step 1: Write failing tests** for old-record backfill from `file_path`, URL fallback decoding, and direct import with Chinese relative paths.
- [ ] **Step 2: Run `mvn -Dtest=PictureMaintenanceServiceTest test`** and confirm failures.
- [ ] **Step 3: Implement services, mapper methods, runner, and scripts**.
- [ ] **Step 4: Re-run focused test** and confirm pass.

### Task 4: Schema And Documentation

**Files:**
- Modify: `db/schema.sql`
- Create: `db/picture-maintenance-migration.sql`
- Modify: `README.md`

- [ ] **Step 1: Update schema for 1024-char `file_URL` and `file_path`, nullable metadata fields, and SHA-256 unique index**.
- [ ] **Step 2: Add production migration SQL with nullable metadata fields before backfill**.
- [ ] **Step 3: Document execution order and scripts**.
- [ ] **Step 4: Run `mvn test` and `mvn package`**.
