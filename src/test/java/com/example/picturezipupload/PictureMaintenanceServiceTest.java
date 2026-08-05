package com.example.picturezipupload;

import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.maintenance.PictureFileInspector;
import com.example.picturezipupload.maintenance.PictureBackfillProgress;
import com.example.picturezipupload.maintenance.PictureBackfillRequest;
import com.example.picturezipupload.maintenance.PictureBackfillResult;
import com.example.picturezipupload.maintenance.PictureMaintenanceReport;
import com.example.picturezipupload.maintenance.PictureMaintenanceRepository;
import com.example.picturezipupload.maintenance.PictureMaintenanceService;
import com.example.picturezipupload.maintenance.StaticPicturePathResolver;
import com.example.picturezipupload.repository.PictureRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PictureMaintenanceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void importsDirectDirectoryWithEncodedChineseFileUrlWithoutCopyingFiles() throws Exception {
        Path sourceRoot = tempDir.resolve("pictures");
        Path image = sourceRoot.resolve("病理 图像").resolve("第一批").resolve("图片 001.png");
        Files.createDirectories(image.getParent());
        Files.write(image, tinyPng());
        InMemoryPictureRecordRepository pictureRepository = new InMemoryPictureRecordRepository();
        PictureMaintenanceService service = service(pictureRepository, new InMemoryPictureMaintenanceRepository());

        PictureMaintenanceReport report = service.importDirectDirectory(
                "medical", sourceRoot, "/api/pictures/files", "data-team", "direct-import-20260701", false);

        assertThat(report.getScanned()).isEqualTo(1);
        assertThat(report.getInserted()).isEqualTo(1);
        assertThat(pictureRepository.inserted).hasSize(1);
        PictureRecord record = pictureRepository.inserted.get(0);
        assertThat(record.getFilePath()).isEqualTo(image.toAbsolutePath().normalize().toString());
        assertThat(record.getFileUrl()).isEqualTo("/api/pictures/files/%E7%97%85%E7%90%86%20%E5%9B%BE%E5%83%8F/"
                + "%E7%AC%AC%E4%B8%80%E6%89%B9/%E5%9B%BE%E7%89%87%20001.png");
        assertThat(record.getOriginalZipName()).isEqualTo("DIRECT:" + sourceRoot.toAbsolutePath().normalize());
        assertThat(Files.exists(image)).isTrue();
    }

    @Test
    void backfillsExistingRecordsFromReliableFilePath() throws Exception {
        Path image = tempDir.resolve("legacy").resolve("中文图片.png");
        Files.createDirectories(image.getParent());
        Files.write(image, tinyPng());
        PictureRecord existing = new PictureRecord();
        existing.setVoiceCode("voice-1");
        existing.setFilePath(image.toString());
        InMemoryPictureMaintenanceRepository maintenanceRepository = new InMemoryPictureMaintenanceRepository();
        maintenanceRepository.recordsMissingMetadata.add(existing);
        InMemoryPictureRecordRepository pictureRepository = new InMemoryPictureRecordRepository();
        PictureMaintenanceService service = service(pictureRepository, maintenanceRepository);

        PictureBackfillResult result = service.backfillExistingRecords(
                backfillRequest(100, 100, null, false), progress -> { });
        PictureMaintenanceReport report = result.report();

        assertThat(report.getBackfilled()).isEqualTo(1);
        assertThat(result.exhausted()).isTrue();
        assertThat(result.lastVoiceCode()).isEqualTo("voice-1");
        assertThat(maintenanceRepository.backfilled).hasSize(1);
        BackfillUpdate update = maintenanceRepository.backfilled.get(0);
        assertThat(update.voiceCode()).isEqualTo("voice-1");
        assertThat(update.fileSize()).isEqualTo(tinyPng().length);
        assertThat(update.uploadId()).isEqualTo("legacy-backfill-20260701");
    }

    @Test
    void backfillUsesOnlyFilePathAndDoesNotFallBackToFileUrl() throws Exception {
        Path root = tempDir.resolve("images");
        Path image = root.resolve("病理").resolve("图片.png");
        Files.createDirectories(image.getParent());
        Files.write(image, tinyPng());
        PictureRecord existing = new PictureRecord();
        existing.setVoiceCode("voice-2");
        existing.setFileUrl("/api/pictures/files/%E7%97%85%E7%90%86/%E5%9B%BE%E7%89%87.png");
        InMemoryPictureMaintenanceRepository maintenanceRepository = new InMemoryPictureMaintenanceRepository();
        maintenanceRepository.recordsMissingMetadata.add(existing);
        PictureMaintenanceService service = service(new InMemoryPictureRecordRepository(), maintenanceRepository);

        PictureMaintenanceReport report = service.backfillExistingRecords(
                backfillRequest(100, 100, null, false), progress -> { }).report();

        assertThat(report.getBackfilled()).isZero();
        assertThat(report.getMissing()).isEqualTo(1);
        assertThat(maintenanceRepository.backfilled).isEmpty();
    }

    @Test
    void backfillPagesByVoiceCodeAndReportsResumableProgress() throws Exception {
        InMemoryPictureMaintenanceRepository maintenanceRepository = new InMemoryPictureMaintenanceRepository();
        for (int index = 1; index <= 5; index++) {
            Path image = tempDir.resolve("legacy").resolve("image-" + index + ".png");
            Files.createDirectories(image.getParent());
            Files.write(image, tinyPng());
            PictureRecord record = new PictureRecord();
            record.setVoiceCode("voice-" + index);
            record.setFilePath(image.toString());
            maintenanceRepository.recordsMissingMetadata.add(record);
        }
        PictureMaintenanceService service = service(
                new InMemoryPictureRecordRepository(), maintenanceRepository);
        List<PictureBackfillProgress> progress = new ArrayList<>();

        PictureBackfillResult result = service.backfillExistingRecords(
                backfillRequest(2, 5, null, true), progress::add);

        assertThat(result.report().getScanned()).isEqualTo(5);
        assertThat(result.report().getBackfilled()).isEqualTo(5);
        assertThat(result.lastVoiceCode()).isEqualTo("voice-5");
        assertThat(result.exhausted()).isTrue();
        assertThat(maintenanceRepository.queryCursors).containsExactly(null, "voice-2", "voice-4", "voice-5");
        assertThat(progress)
                .extracting(PictureBackfillProgress::lastVoiceCode)
                .containsExactly("voice-2", "voice-4", "voice-5");
    }

    @Test
    void backfillStartsAfterCheckpointCursor() throws Exception {
        Path first = tempDir.resolve("legacy/first.png");
        Path second = tempDir.resolve("legacy/second.png");
        Files.createDirectories(first.getParent());
        Files.write(first, tinyPng());
        Files.write(second, tinyPng());
        InMemoryPictureMaintenanceRepository maintenanceRepository = new InMemoryPictureMaintenanceRepository();
        maintenanceRepository.recordsMissingMetadata.add(record("voice-1", first));
        maintenanceRepository.recordsMissingMetadata.add(record("voice-2", second));
        PictureMaintenanceService service = service(
                new InMemoryPictureRecordRepository(), maintenanceRepository);

        PictureBackfillResult result = service.backfillExistingRecords(
                backfillRequest(100, 100, "voice-1", true), progress -> { });

        assertThat(result.report().getScanned()).isEqualTo(1);
        assertThat(result.lastVoiceCode()).isEqualTo("voice-2");
        assertThat(maintenanceRepository.queryCursors).containsExactly("voice-1");
    }

    private static PictureBackfillRequest backfillRequest(int batchSize, int limit, String afterVoiceCode,
                                                           boolean dryRun) {
        return new PictureBackfillRequest(
                "medical",
                "data-team",
                "legacy-backfill-20260701",
                batchSize,
                limit,
                2,
                afterVoiceCode,
                dryRun);
    }

    private static PictureRecord record(String voiceCode, Path filePath) {
        PictureRecord record = new PictureRecord();
        record.setVoiceCode(voiceCode);
        record.setFilePath(filePath.toString());
        return record;
    }

    private PictureMaintenanceService service(InMemoryPictureRecordRepository pictureRepository,
                                              InMemoryPictureMaintenanceRepository maintenanceRepository) {
        return new PictureMaintenanceService(
                pictureRepository,
                maintenanceRepository,
                new PictureFileInspector(1024 * 1024),
                new StaticPicturePathResolver());
    }

    private static byte[] tinyPng() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }

    private static final class InMemoryPictureRecordRepository implements PictureRecordRepository {
        private final List<PictureRecord> inserted = new ArrayList<>();

        @Override
        public Optional<PictureRecord> findByContentSha256(String businessArea, String contentSha256) {
            return inserted.stream()
                    .filter(record -> contentSha256.equals(record.getContentSha256()))
                    .findFirst();
        }

        @Override
        public void insert(String businessArea, PictureRecord record) {
            inserted.add(record);
        }

        @Override
        public void updateDuplicateImport(String businessArea, String contentSha256, String filename, String extname,
                                          String uploadId, String originalZipName, String operator,
                                          LocalDateTime updateTime) {
        }
    }

    private static final class InMemoryPictureMaintenanceRepository implements PictureMaintenanceRepository {
        private final List<PictureRecord> recordsMissingMetadata = new ArrayList<>();
        private final List<BackfillUpdate> backfilled = new ArrayList<>();
        private final List<String> queryCursors = new ArrayList<>();

        @Override
        public List<PictureRecord> findRecordsMissingMetadata(String businessArea, String afterVoiceCode, int limit) {
            queryCursors.add(afterVoiceCode);
            return recordsMissingMetadata.stream()
                    .filter(record -> afterVoiceCode == null || record.getVoiceCode().compareTo(afterVoiceCode) > 0)
                    .sorted((left, right) -> left.getVoiceCode().compareTo(right.getVoiceCode()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void updateBackfillMetadata(String businessArea, String voiceCode, String contentSha256, long fileSize,
                                           String uploadId, String originalZipName, String operator,
                                           LocalDateTime updateTime) {
            backfilled.add(new BackfillUpdate(voiceCode, contentSha256, fileSize, uploadId));
        }
    }

    private record BackfillUpdate(String voiceCode, String contentSha256, long fileSize, String uploadId) {
    }
}
