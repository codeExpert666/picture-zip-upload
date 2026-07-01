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
        /**
         * 上传任务唯一标识。
         */
        String uploadId,
        /**
         * 用户上传的原始 zip 文件名。
         */
        String originalFilename,
        /**
         * 当前任务状态，前端可据此判断是否允许继续补传分片。
         */
        UploadStatus status,
        /**
         * 前端声明的分片总数。
         */
        int totalChunks,
        /**
         * 服务端已成功接收并落盘的分片数量。
         */
        int uploadedChunks,
        /**
         * 已上传分片的序号集合，序号从 0 开始。
         */
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
