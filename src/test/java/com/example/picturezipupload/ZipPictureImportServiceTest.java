package com.example.picturezipupload;

import com.example.picturezipupload.config.PictureUploadProperties;
import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.domain.UploadTaskProgress;
import com.example.picturezipupload.importing.ZipPictureImportService;
import com.example.picturezipupload.progress.InMemoryUploadProgressStore;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ZipPictureImportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesDuplicateImageContentOnlyOnceAndUpdatesExistingRecord() throws Exception {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setRootPath(tempDir);
        properties.setPublicUrlPrefix("/api/pictures/files");
        InMemoryPictureRecordRepository repository = new InMemoryPictureRecordRepository();
        InMemoryUploadProgressStore progressStore = new InMemoryUploadProgressStore();
        ZipPictureImportService service = new ZipPictureImportService(properties, repository, progressStore);
        Path zip = tempDir.resolve("upload.zip");

        createZip(zip,
                new ZipImage("first.png", tinyPng()),
                new ZipImage("renamed.png", tinyPng()));

        service.importZip("upload-1", "dataset.zip", zip);

        assertThat(repository.inserted).hasSize(1);
        assertThat(repository.duplicateUpdates).hasSize(1);
        assertThat(Files.walk(tempDir.resolve("images"))
                .filter(Files::isRegularFile)
                .count()).isEqualTo(1);

        UploadTaskProgress progress = progressStore.get("upload-1").orElseThrow();
        assertThat(progress.getProcessedFiles()).isEqualTo(2);
        assertThat(progress.getInserted()).isEqualTo(1);
        assertThat(progress.getDuplicated()).isEqualTo(1);
        assertThat(progress.getFailed()).isZero();
    }

    private static void createZip(Path zip, ZipImage... images) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (ZipImage image : images) {
                output.putNextEntry(new ZipEntry(image.name()));
                output.write(image.bytes());
                output.closeEntry();
            }
        }
    }

    private static byte[] tinyPng() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }

    private record ZipImage(String name, byte[] bytes) {
    }

    private static final class InMemoryPictureRecordRepository implements PictureRecordRepository {
        private final List<PictureRecord> inserted = new ArrayList<>();
        private final List<PictureRecord> duplicateUpdates = new ArrayList<>();

        @Override
        public Optional<PictureRecord> findByContentSha256(String contentSha256) {
            return inserted.stream()
                    .filter(record -> record.getContentSha256().equals(contentSha256))
                    .findFirst();
        }

        @Override
        public void insert(PictureRecord record) {
            inserted.add(record);
        }

        @Override
        public void updateDuplicateImport(String contentSha256, String filename, String extname,
                                          String uploadId, String originalZipName, LocalDateTime updateTime) {
            findByContentSha256(contentSha256).ifPresent(duplicateUpdates::add);
        }
    }
}
