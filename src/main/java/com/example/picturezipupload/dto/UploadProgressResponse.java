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
        /**
         * 上传任务唯一标识。
         */
        String uploadId,
        /**
         * 用户上传的原始 zip 文件名。
         */
        String originalFilename,
        /**
         * 当前任务状态，覆盖分片上传、合并 zip 和后台导入阶段。
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
         * zip 内待处理的有效图片总数。
         */
        long totalFiles,
        /**
         * 已完成导入、判重或失败处理的图片数量。
         */
        long processedFiles,
        /**
         * 本次导入新增入库的图片数量。
         */
        long inserted,
        /**
         * 本次导入命中内容重复并更新既有记录的图片数量。
         */
        long duplicated,
        /**
         * 本次导入被过滤或处理失败的图片数量。
         */
        long failed,
        /**
         * 当前任务补充说明，失败时通常包含失败原因。
         */
        String message,
        /**
         * 上传任务创建时间。
         */
        LocalDateTime createdAt,
        /**
         * 上传任务最近一次状态或统计更新时间。
         */
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
