package com.example.picturezipupload.dto;

import com.example.picturezipupload.domain.UploadStatus;
import com.example.picturezipupload.domain.UploadTaskProgress;

import java.time.LocalDateTime;

public record UploadProgressResponse(
        String uploadId,
        String originalFilename,
        UploadStatus status,
        int totalChunks,
        int uploadedChunks,
        long processedFiles,
        long inserted,
        long duplicated,
        long failed,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UploadProgressResponse from(UploadTaskProgress progress) {
        return new UploadProgressResponse(
                progress.getUploadId(),
                progress.getOriginalFilename(),
                progress.getStatus(),
                progress.getTotalChunks(),
                progress.getUploadedChunks(),
                progress.getProcessedFiles(),
                progress.getInserted(),
                progress.getDuplicated(),
                progress.getFailed(),
                progress.getMessage(),
                progress.getCreatedAt(),
                progress.getUpdatedAt());
    }
}
