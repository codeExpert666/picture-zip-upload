package com.example.picturezipupload.service;

import com.example.picturezipupload.config.BusinessAreaTableResolver;
import com.example.picturezipupload.domain.UploadTaskProgress;
import com.example.picturezipupload.dto.CreateUploadRequest;
import com.example.picturezipupload.dto.CreateUploadResponse;
import com.example.picturezipupload.dto.UploadedChunksResponse;
import com.example.picturezipupload.dto.UploadProgressResponse;
import com.example.picturezipupload.importing.ZipPictureImportService;
import com.example.picturezipupload.progress.UploadProgressStore;
import com.example.picturezipupload.storage.FileUploadStorageService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 压缩包上传应用服务。
 *
 * <p>该类只编排任务状态、分片存储和后台导入，不直接处理 zip 条目或数据库判重。</p>
 */
@Service
public class PictureZipUploadService {

    private final FileUploadStorageService storageService;
    private final ZipPictureImportService importService;
    private final UploadProgressStore progressStore;
    private final BusinessAreaTableResolver tableResolver;

    public PictureZipUploadService(FileUploadStorageService storageService, ZipPictureImportService importService,
                                   UploadProgressStore progressStore, BusinessAreaTableResolver tableResolver) {
        this.storageService = storageService;
        this.importService = importService;
        this.progressStore = progressStore;
        this.tableResolver = tableResolver;
    }

    /**
     * 创建上传任务并初始化进度。
     */
    public CreateUploadResponse createUpload(CreateUploadRequest request) {
        String businessArea = requireText(request.getBusinessArea(), "业务领域不能为空");
        String operator = requireText(request.getOperator(), "上传责任人不能为空");
        if (operator.length() > 50) {
            throw new IllegalArgumentException("上传责任人不能超过 50 个字符");
        }
        tableResolver.resolve(businessArea);
        String uploadId = UUID.randomUUID().toString();
        UploadTaskProgress progress = UploadTaskProgress.created(
                uploadId,
                request.getOriginalFilename(),
                request.getTotalChunks(),
                businessArea,
                operator);
        progressStore.save(progress);
        return new CreateUploadResponse(uploadId, progress.getStatus());
    }

    /**
     * 保存单个分片并更新已上传分片数。
     */
    public UploadProgressResponse uploadChunk(String uploadId, int chunkIndex, InputStream inputStream) throws IOException {
        return uploadChunk(uploadId, chunkIndex, inputStream, null, null);
    }

    /**
     * 保存单个分片，按需校验分片内容摘要，并更新已上传分片数。
     */
    public UploadProgressResponse uploadChunk(String uploadId, int chunkIndex, InputStream inputStream,
                                              String checksumAlgorithm, String checksum) throws IOException {
        UploadTaskProgress progress = loadProgress(uploadId);
        storageService.saveChunk(uploadId, chunkIndex, inputStream, checksumAlgorithm, checksum);
        progress.recordUploadedChunks(storageService.listUploadedChunkIndexes(uploadId).size());
        progressStore.save(progress);
        return UploadProgressResponse.from(progress);
    }

    /**
     * 合并所有分片并异步导入 zip，接口立即返回 PROCESSING 状态供前端轮询。
     */
    public UploadProgressResponse complete(String uploadId) throws IOException {
        UploadTaskProgress progress = loadProgress(uploadId);
        progress.markMerging();
        progressStore.save(progress);

        Path zipPath = storageService.mergeChunks(uploadId, progress.getOriginalFilename(), progress.getTotalChunks());
        progress.markProcessing();
        progressStore.save(progress);
        importService.importZipAsync(
                uploadId,
                progress.getOriginalFilename(),
                progress.getBusinessArea(),
                progress.getOperator(),
                zipPath);
        return UploadProgressResponse.from(progress);
    }

    public UploadProgressResponse progress(String uploadId) {
        return UploadProgressResponse.from(loadProgress(uploadId));
    }

    /**
     * 查询已落盘分片序号，供前端断点续传时跳过已上传分片。
     */
    public UploadedChunksResponse uploadedChunks(String uploadId) throws IOException {
        UploadTaskProgress progress = loadProgress(uploadId);
        List<Integer> uploadedChunkIndexes = storageService.listUploadedChunkIndexes(uploadId);
        return UploadedChunksResponse.from(progress, uploadedChunkIndexes);
    }

    /**
     * 取消尚未进入合并或导入阶段的上传任务，并清理已落盘分片。
     */
    public void cancelUpload(String uploadId) throws IOException {
        UploadTaskProgress progress = loadProgress(uploadId);
        ensureCancelable(progress);
        storageService.deleteChunks(uploadId);
        progressStore.delete(uploadId);
    }

    private UploadTaskProgress loadProgress(String uploadId) {
        return progressStore.get(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传任务不存在: " + uploadId));
    }

    private static void ensureCancelable(UploadTaskProgress progress) {
        switch (progress.getStatus()) {
            case CREATED, UPLOADING, FAILED -> {
                return;
            }
            case MERGING, PROCESSING, DONE ->
                    throw new IllegalArgumentException("上传任务当前状态不能取消: " + progress.getStatus());
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
