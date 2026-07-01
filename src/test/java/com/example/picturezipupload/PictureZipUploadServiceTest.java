package com.example.picturezipupload;

import com.example.picturezipupload.config.PictureUploadProperties;
import com.example.picturezipupload.config.BusinessAreaTableResolver;
import com.example.picturezipupload.domain.UploadStatus;
import com.example.picturezipupload.domain.UploadTaskProgress;
import com.example.picturezipupload.dto.CreateUploadRequest;
import com.example.picturezipupload.dto.CreateUploadResponse;
import com.example.picturezipupload.dto.UploadedChunksResponse;
import com.example.picturezipupload.progress.InMemoryUploadProgressStore;
import com.example.picturezipupload.service.PictureZipUploadService;
import com.example.picturezipupload.storage.FileUploadStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PictureZipUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createUploadStoresBusinessAreaAndOperatorForAsyncImport() {
        InMemoryUploadProgressStore progressStore = new InMemoryUploadProgressStore();
        PictureZipUploadService service = new PictureZipUploadService(
                storageService(),
                null,
                progressStore,
                tableResolver());
        CreateUploadRequest request = createRequest("medical", "alice");

        CreateUploadResponse response = service.createUpload(request);

        UploadTaskProgress progress = progressStore.get(response.uploadId()).orElseThrow();
        assertThat(progress.getBusinessArea()).isEqualTo("medical");
        assertThat(progress.getOperator()).isEqualTo("alice");
    }

    @Test
    void createUploadRejectsUnknownBusinessAreaBeforeReceivingChunks() {
        InMemoryUploadProgressStore progressStore = new InMemoryUploadProgressStore();
        PictureZipUploadService service = new PictureZipUploadService(
                storageService(),
                null,
                progressStore,
                tableResolver());
        CreateUploadRequest request = createRequest("unknown", "alice");

        assertThatThrownBy(() -> service.createUpload(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的业务领域");
    }

    @Test
    void returnsUploadedChunkIndexesForResume() throws Exception {
        FileUploadStorageService storageService = storageService();
        InMemoryUploadProgressStore progressStore = progressStore("upload-1", "dataset.zip", 4);
        PictureZipUploadService service = uploadService(storageService, progressStore);
        storageService.saveChunk("upload-1", 2, new ByteArrayInputStream("two".getBytes()));
        storageService.saveChunk("upload-1", 0, new ByteArrayInputStream("zero".getBytes()));

        UploadedChunksResponse response = service.uploadedChunks("upload-1");

        assertThat(response.uploadId()).isEqualTo("upload-1");
        assertThat(response.originalFilename()).isEqualTo("dataset.zip");
        assertThat(response.status()).isEqualTo(UploadStatus.CREATED);
        assertThat(response.totalChunks()).isEqualTo(4);
        assertThat(response.uploadedChunks()).isEqualTo(2);
        assertThat(response.uploadedChunkIndexes()).containsExactly(0, 2);
    }

    @Test
    void keepsUploadedChunkCountDistinctWhenRetryingSameChunk() throws Exception {
        FileUploadStorageService storageService = storageService();
        InMemoryUploadProgressStore progressStore = progressStore("upload-1", "dataset.zip", 4);
        PictureZipUploadService service = uploadService(storageService, progressStore);

        service.uploadChunk("upload-1", 1, new ByteArrayInputStream("first".getBytes()));
        service.uploadChunk("upload-1", 1, new ByteArrayInputStream("retry".getBytes()));

        UploadTaskProgress progress = progressStore.get("upload-1").orElseThrow();
        assertThat(progress.getUploadedChunks()).isEqualTo(1);
        assertThat(progress.getStatus()).isEqualTo(UploadStatus.UPLOADING);
    }

    @Test
    void uploadsChunkWhenChecksumMatches() throws Exception {
        FileUploadStorageService storageService = storageService();
        InMemoryUploadProgressStore progressStore = progressStore("upload-1", "dataset.zip", 4);
        PictureZipUploadService service = uploadService(storageService, progressStore);

        service.uploadChunk(
                "upload-1",
                1,
                new ByteArrayInputStream("chunk".getBytes(StandardCharsets.UTF_8)),
                "SHA-256",
                hexDigest("SHA-256", "chunk"));

        UploadTaskProgress progress = progressStore.get("upload-1").orElseThrow();
        assertThat(progress.getUploadedChunks()).isEqualTo(1);
        assertThat(storageService.listUploadedChunkIndexes("upload-1")).containsExactly(1);
    }

    @Test
    void rejectsChunkWhenChecksumMismatches() throws Exception {
        FileUploadStorageService storageService = storageService();
        InMemoryUploadProgressStore progressStore = progressStore("upload-1", "dataset.zip", 4);
        PictureZipUploadService service = uploadService(storageService, progressStore);

        assertThatThrownBy(() -> service.uploadChunk(
                "upload-1",
                1,
                new ByteArrayInputStream("chunk".getBytes(StandardCharsets.UTF_8)),
                "MD5",
                "00000000000000000000000000000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片校验失败");

        UploadTaskProgress progress = progressStore.get("upload-1").orElseThrow();
        assertThat(progress.getUploadedChunks()).isZero();
        assertThat(storageService.listUploadedChunkIndexes("upload-1")).isEmpty();
    }

    @Test
    void cancelsUnfinishedUploadByDeletingChunksAndProgress() throws Exception {
        FileUploadStorageService storageService = storageService();
        InMemoryUploadProgressStore progressStore = progressStore("upload-1", "dataset.zip", 4);
        PictureZipUploadService service = uploadService(storageService, progressStore);
        storageService.saveChunk("upload-1", 0, new ByteArrayInputStream("zero".getBytes()));
        storageService.saveChunk("upload-1", 2, new ByteArrayInputStream("two".getBytes()));

        service.cancelUpload("upload-1");

        assertThat(storageService.listUploadedChunkIndexes("upload-1")).isEmpty();
        assertThat(progressStore.get("upload-1")).isEmpty();
        assertThat(tempDir.resolve("chunks/upload-1")).doesNotExist();
    }

    @Test
    void rejectsCancelAfterUploadStartsProcessing() throws Exception {
        FileUploadStorageService storageService = storageService();
        InMemoryUploadProgressStore progressStore = progressStore("upload-1", "dataset.zip", 4);
        UploadTaskProgress progress = progressStore.get("upload-1").orElseThrow();
        progress.markProcessing();
        progressStore.save(progress);
        PictureZipUploadService service = uploadService(storageService, progressStore);
        storageService.saveChunk("upload-1", 0, new ByteArrayInputStream("zero".getBytes()));

        assertThatThrownBy(() -> service.cancelUpload("upload-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前状态不能取消");

        assertThat(storageService.listUploadedChunkIndexes("upload-1")).containsExactly(0);
        assertThat(progressStore.get("upload-1")).isPresent();
    }

    private FileUploadStorageService storageService() {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setRootPath(tempDir);
        return new FileUploadStorageService(properties);
    }

    private PictureZipUploadService uploadService(FileUploadStorageService storageService,
                                                  InMemoryUploadProgressStore progressStore) {
        return new PictureZipUploadService(storageService, null, progressStore, tableResolver());
    }

    private static BusinessAreaTableResolver tableResolver() {
        return new BusinessAreaTableResolver(Map.of("medical", "medical_corpus_analysis_picture"));
    }

    private static CreateUploadRequest createRequest(String businessArea, String operator) {
        CreateUploadRequest request = new CreateUploadRequest();
        request.setOriginalFilename("dataset.zip");
        request.setTotalChunks(4);
        request.setTotalSize(1024);
        request.setBusinessArea(businessArea);
        request.setOperator(operator);
        return request;
    }

    private static InMemoryUploadProgressStore progressStore(String uploadId, String originalFilename, int totalChunks) {
        InMemoryUploadProgressStore progressStore = new InMemoryUploadProgressStore();
        progressStore.save(UploadTaskProgress.created(uploadId, originalFilename, totalChunks));
        return progressStore;
    }

    private static String hexDigest(String algorithm, String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
