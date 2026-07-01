package com.example.picturezipupload.dto;

import com.example.picturezipupload.domain.UploadStatus;
import com.example.picturezipupload.domain.UploadTaskProgress;

import java.util.List;

/**
 * 已上传分片列表响应。
 *
 * <p>前端刷新页面后可据此跳过已完成分片，只补传缺失分片。</p>
 */
public record UploadedChunksResponse(
        String uploadId,
        String originalFilename,
        UploadStatus status,
        int totalChunks,
        int uploadedChunks,
        List<Integer> uploadedChunkIndexes
) {
    public UploadedChunksResponse {
        uploadedChunkIndexes = List.copyOf(uploadedChunkIndexes);
    }

    public static UploadedChunksResponse from(UploadTaskProgress progress, List<Integer> uploadedChunkIndexes) {
        return new UploadedChunksResponse(
                progress.getUploadId(),
                progress.getOriginalFilename(),
                progress.getStatus(),
                progress.getTotalChunks(),
                uploadedChunkIndexes.size(),
                uploadedChunkIndexes);
    }
}
