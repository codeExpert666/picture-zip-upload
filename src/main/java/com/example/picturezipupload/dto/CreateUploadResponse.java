package com.example.picturezipupload.dto;

import com.example.picturezipupload.domain.UploadStatus;

/**
 * 创建上传任务后的响应。
 */
public record CreateUploadResponse(
        /**
         * 上传任务唯一标识，后续上传分片、完成合并和查询进度均使用该值。
         */
        String uploadId,
        /**
         * 上传任务初始状态，通常为等待分片上传的 CREATED。
         */
        UploadStatus status
) {
}
