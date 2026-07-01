package com.example.picturezipupload;

import com.example.picturezipupload.config.PictureUploadProperties;
import com.example.picturezipupload.domain.UploadStatus;
import com.example.picturezipupload.domain.UploadTaskProgress;
import com.example.picturezipupload.dto.UploadedChunksResponse;
import com.example.picturezipupload.progress.InMemoryUploadProgressStore;
import com.example.picturezipupload.service.PictureZipUploadService;
import com.example.picturezipupload.storage.FileUploadStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PictureZipUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsUploadedChunkIndexesForResume() throws Exception {
        FileUploadStorageService storageService = storageService();
        InMemoryUploadProgressStore progressStore = progressStore("upload-1", "dataset.zip", 4);
        PictureZipUploadService service = new PictureZipUploadService(storageService, null, progressStore);
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
        PictureZipUploadService service = new PictureZipUploadService(storageService, null, progressStore);

        service.uploadChunk("upload-1", 1, new ByteArrayInputStream("first".getBytes()));
        service.uploadChunk("upload-1", 1, new ByteArrayInputStream("retry".getBytes()));

        UploadTaskProgress progress = progressStore.get("upload-1").orElseThrow();
        assertThat(progress.getUploadedChunks()).isEqualTo(1);
        assertThat(progress.getStatus()).isEqualTo(UploadStatus.UPLOADING);
    }

    private FileUploadStorageService storageService() {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setRootPath(tempDir);
        return new FileUploadStorageService(properties);
    }

    private static InMemoryUploadProgressStore progressStore(String uploadId, String originalFilename, int totalChunks) {
        InMemoryUploadProgressStore progressStore = new InMemoryUploadProgressStore();
        progressStore.save(UploadTaskProgress.created(uploadId, originalFilename, totalChunks));
        return progressStore;
    }
}
