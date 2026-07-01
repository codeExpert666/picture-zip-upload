package com.example.picturezipupload.service;

import com.example.picturezipupload.domain.UploadTaskProgress;
import com.example.picturezipupload.dto.CreateUploadRequest;
import com.example.picturezipupload.dto.CreateUploadResponse;
import com.example.picturezipupload.dto.UploadProgressResponse;
import com.example.picturezipupload.importing.ZipPictureImportService;
import com.example.picturezipupload.progress.UploadProgressStore;
import com.example.picturezipupload.storage.FileUploadStorageService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class PictureZipUploadService {

    private final FileUploadStorageService storageService;
    private final ZipPictureImportService importService;
    private final UploadProgressStore progressStore;

    public PictureZipUploadService(FileUploadStorageService storageService, ZipPictureImportService importService,
                                   UploadProgressStore progressStore) {
        this.storageService = storageService;
        this.importService = importService;
        this.progressStore = progressStore;
    }

    public CreateUploadResponse createUpload(CreateUploadRequest request) {
        String uploadId = UUID.randomUUID().toString();
        UploadTaskProgress progress = UploadTaskProgress.created(
                uploadId,
                request.getOriginalFilename(),
                request.getTotalChunks());
        progressStore.save(progress);
        return new CreateUploadResponse(uploadId, progress.getStatus());
    }

    public UploadProgressResponse uploadChunk(String uploadId, int chunkIndex, InputStream inputStream) throws IOException {
        UploadTaskProgress progress = loadProgress(uploadId);
        storageService.saveChunk(uploadId, chunkIndex, inputStream);
        progress.recordChunkUploaded();
        progressStore.save(progress);
        return UploadProgressResponse.from(progress);
    }

    public UploadProgressResponse complete(String uploadId) throws IOException {
        UploadTaskProgress progress = loadProgress(uploadId);
        progress.markMerging();
        progressStore.save(progress);

        Path zipPath = storageService.mergeChunks(uploadId, progress.getOriginalFilename(), progress.getTotalChunks());
        progress.markProcessing();
        progressStore.save(progress);
        importService.importZipAsync(uploadId, progress.getOriginalFilename(), zipPath);
        return UploadProgressResponse.from(progress);
    }

    public UploadProgressResponse progress(String uploadId) {
        return UploadProgressResponse.from(loadProgress(uploadId));
    }

    private UploadTaskProgress loadProgress(String uploadId) {
        return progressStore.get(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传任务不存在: " + uploadId));
    }
}
