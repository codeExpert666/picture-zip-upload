package com.example.picturezipupload.dto;

import com.example.picturezipupload.domain.UploadStatus;
import com.example.picturezipupload.domain.UploadTaskProgress;

import java.time.LocalDateTime;

/**
 * 上传任务进度响应。
 *
 * <p>包含分片上传进度和后台图片导入统计，供前端轮询展示。</p>
 */
public record UploadProgressResponse(
        String uploadId,
        String originalFilename,
        UploadStatus status,
        int totalChunks,
        int uploadedChunks,
        long totalFiles,
        long processedFiles,
        long inserted,
        long duplicated,
        long failed,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 将领域进度对象转换为接口响应，避免 Controller 直接暴露可变对象。
     */
    public static UploadProgressResponse from(UploadTaskProgress progress) {
        return new UploadProgressResponse(
                progress.getUploadId(),
                progress.getOriginalFilename(),
                progress.getStatus(),
                progress.getTotalChunks(),
                progress.getUploadedChunks(),
                progress.getTotalFiles(),
                progress.getProcessedFiles(),
                progress.getInserted(),
                progress.getDuplicated(),
                progress.getFailed(),
                progress.getMessage(),
                progress.getCreatedAt(),
                progress.getUpdatedAt());
    }
}
