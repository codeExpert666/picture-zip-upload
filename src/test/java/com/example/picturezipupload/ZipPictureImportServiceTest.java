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
        Path workRoot = tempDir.resolve("work");
        Path imageRoot = tempDir.resolve("pictures");
        properties.setWorkRootPath(workRoot);
        properties.setImageRootPath(imageRoot);
        properties.setPublicUrlPrefix("/api/pictures/files");
        InMemoryPictureRecordRepository repository = new InMemoryPictureRecordRepository();
        InMemoryUploadProgressStore progressStore = new InMemoryUploadProgressStore();
        ZipPictureImportService service = new ZipPictureImportService(properties, repository, progressStore);
        Path zip = tempDir.resolve("upload.zip");

        createZip(zip,
                new ZipImage("first.png", tinyPng()),
                new ZipImage("renamed.png", tinyPng()));

        service.importZip("upload-1", "dataset.zip", "medical", "alice", zip);

        assertThat(repository.inserted).hasSize(1);
        assertThat(repository.duplicateUpdates).hasSize(1);
        assertThat(repository.insertBusinessAreas).containsExactly("medical");
        assertThat(repository.inserted.get(0).getOperator()).isEqualTo("alice");
        assertThat(repository.inserted.get(0).getFileUrl()).startsWith("/api/pictures/files/");
        assertThat(repository.inserted.get(0).getFilePath()).startsWith(imageRoot.toAbsolutePath().toString());
        assertThat(repository.duplicateUpdates.get(0).businessArea()).isEqualTo("medical");
        assertThat(repository.duplicateUpdates.get(0).operator()).isEqualTo("alice");
        assertThat(Files.walk(imageRoot)
                .filter(Files::isRegularFile)
                .count()).isEqualTo(1);
        assertThat(workRoot.resolve("images")).doesNotExist();

        UploadTaskProgress progress = progressStore.get("upload-1").orElseThrow();
        assertThat(progress.getTotalFiles()).isEqualTo(2);
        assertThat(progress.getProcessedFiles()).isEqualTo(2);
        assertThat(progress.getInserted()).isEqualTo(1);
        assertThat(progress.getDuplicated()).isEqualTo(1);
        assertThat(progress.getFailed()).isZero();
    }

    @Test
    void countsNonDirectoryEntriesBeforeImportingFiles() throws Exception {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setWorkRootPath(tempDir.resolve("work"));
        properties.setImageRootPath(tempDir.resolve("pictures"));
        properties.setPublicUrlPrefix("/api/pictures/files");
        InMemoryPictureRecordRepository repository = new InMemoryPictureRecordRepository();
        InMemoryUploadProgressStore progressStore = new InMemoryUploadProgressStore();
        ZipPictureImportService service = new ZipPictureImportService(properties, repository, progressStore);
        Path zip = tempDir.resolve("upload-with-invalid-files.zip");

        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("valid.png"));
            output.write(tinyPng());
            output.closeEntry();
            output.putNextEntry(new ZipEntry("folder/nested.png"));
            output.write(tinyPng());
            output.closeEntry();
            output.putNextEntry(new ZipEntry("notes.txt"));
            output.write("plain text".getBytes());
            output.closeEntry();
        }

        service.importZip("upload-2", "dataset.zip", "medical", "alice", zip);

        UploadTaskProgress progress = progressStore.get("upload-2").orElseThrow();
        assertThat(progress.getTotalFiles()).isEqualTo(3);
        assertThat(progress.getProcessedFiles()).isEqualTo(3);
        assertThat(progress.getInserted()).isEqualTo(1);
        assertThat(progress.getDuplicated()).isEqualTo(1);
        assertThat(progress.getFailed()).isEqualTo(1);
    }

    @Test
    void importsNestedZipImagesAndStoresOnlyBaseFilename() throws Exception {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setWorkRootPath(tempDir.resolve("work"));
        properties.setImageRootPath(tempDir.resolve("pictures"));
        properties.setPublicUrlPrefix("/api/pictures/files");
        InMemoryPictureRecordRepository repository = new InMemoryPictureRecordRepository();
        InMemoryUploadProgressStore progressStore = new InMemoryUploadProgressStore();
        ZipPictureImportService service = new ZipPictureImportService(properties, repository, progressStore);
        Path zip = tempDir.resolve("nested-upload.zip");

        createZip(zip, new ZipImage("病理图像/第一批/中文图片.png", tinyPng()));

        service.importZip("upload-3", "dataset.zip", "medical", "alice", zip);

        assertThat(repository.inserted).hasSize(1);
        assertThat(repository.inserted.get(0).getFilename()).isEqualTo("中文图片");
        UploadTaskProgress progress = progressStore.get("upload-3").orElseThrow();
        assertThat(progress.getTotalFiles()).isEqualTo(1);
        assertThat(progress.getInserted()).isEqualTo(1);
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
        private final List<String> insertBusinessAreas = new ArrayList<>();
        private final List<DuplicateUpdate> duplicateUpdates = new ArrayList<>();

        @Override
        public Optional<PictureRecord> findByContentSha256(String businessArea, String contentSha256) {
            return inserted.stream()
                    .filter(record -> record.getContentSha256().equals(contentSha256))
                    .findFirst();
        }

        @Override
        public void insert(String businessArea, PictureRecord record) {
            insertBusinessAreas.add(businessArea);
            inserted.add(record);
        }

        @Override
        public void updateDuplicateImport(String businessArea, String contentSha256, String filename, String extname,
                                          String uploadId, String originalZipName, String operator,
                                          LocalDateTime updateTime) {
            duplicateUpdates.add(new DuplicateUpdate(businessArea, contentSha256, operator));
            findByContentSha256(businessArea, contentSha256).ifPresent(record -> record.setOperator(operator));
        }
    }

    private record DuplicateUpdate(String businessArea, String contentSha256, String operator) {
    }
}
