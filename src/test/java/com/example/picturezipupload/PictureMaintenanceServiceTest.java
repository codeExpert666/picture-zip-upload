package com.example.picturezipupload;

import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.maintenance.PictureFileInspector;
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
import java.util.Map;
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
        PictureMaintenanceService service = service(pictureRepository, new InMemoryPictureMaintenanceRepository(),
                Map.of("/api/pictures/direct", sourceRoot));

        PictureMaintenanceReport report = service.importDirectDirectory(
                "medical", sourceRoot, "/api/pictures/direct", "data-team", "direct-import-20260701", false);

        assertThat(report.getScanned()).isEqualTo(1);
        assertThat(report.getInserted()).isEqualTo(1);
        assertThat(pictureRepository.inserted).hasSize(1);
        PictureRecord record = pictureRepository.inserted.get(0);
        assertThat(record.getFilePath()).isEqualTo(image.toAbsolutePath().normalize().toString());
        assertThat(record.getFileUrl()).isEqualTo("/api/pictures/direct/%E7%97%85%E7%90%86%20%E5%9B%BE%E5%83%8F/"
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
        PictureMaintenanceService service = service(pictureRepository, maintenanceRepository, Map.of());

        PictureMaintenanceReport report = service.backfillExistingRecords(
                "medical", null, "data-team", "legacy-backfill-20260701", 100, false);

        assertThat(report.getBackfilled()).isEqualTo(1);
        assertThat(maintenanceRepository.backfilled).hasSize(1);
        BackfillUpdate update = maintenanceRepository.backfilled.get(0);
        assertThat(update.voiceCode()).isEqualTo("voice-1");
        assertThat(update.fileSize()).isEqualTo(tinyPng().length);
        assertThat(update.uploadId()).isEqualTo("legacy-backfill-20260701");
    }

    @Test
    void backfillsExistingRecordsByDecodingFileUrlWhenFilePathIsMissing() throws Exception {
        Path root = tempDir.resolve("images");
        Path image = root.resolve("病理").resolve("图片.png");
        Files.createDirectories(image.getParent());
        Files.write(image, tinyPng());
        PictureRecord existing = new PictureRecord();
        existing.setVoiceCode("voice-2");
        existing.setFileUrl("/api/pictures/files/%E7%97%85%E7%90%86/%E5%9B%BE%E7%89%87.png");
        InMemoryPictureMaintenanceRepository maintenanceRepository = new InMemoryPictureMaintenanceRepository();
        maintenanceRepository.recordsMissingMetadata.add(existing);
        PictureMaintenanceService service = service(new InMemoryPictureRecordRepository(), maintenanceRepository,
                Map.of("/api/pictures/files", root));

        PictureMaintenanceReport report = service.backfillExistingRecords(
                "medical", null, "data-team", "legacy-backfill-20260701", 100, false);

        assertThat(report.getBackfilled()).isEqualTo(1);
        assertThat(maintenanceRepository.backfilled).hasSize(1);
    }

    private PictureMaintenanceService service(InMemoryPictureRecordRepository pictureRepository,
                                              InMemoryPictureMaintenanceRepository maintenanceRepository,
                                              Map<String, Path> staticMappings) {
        return new PictureMaintenanceService(
                pictureRepository,
                maintenanceRepository,
                new PictureFileInspector(1024 * 1024),
                new StaticPicturePathResolver(staticMappings));
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

        @Override
        public List<PictureRecord> findRecordsMissingMetadata(String businessArea, int limit) {
            return recordsMissingMetadata.stream().limit(limit).toList();
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
