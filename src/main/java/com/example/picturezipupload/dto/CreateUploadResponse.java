package com.example.picturezipupload.dto;

import com.example.picturezipupload.domain.UploadStatus;

/**
 * 创建上传任务后的响应。
 */
public record CreateUploadResponse(String uploadId, UploadStatus status) {
}
