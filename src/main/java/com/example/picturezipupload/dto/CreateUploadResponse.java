package com.example.picturezipupload.dto;

import com.example.picturezipupload.domain.UploadStatus;

public record CreateUploadResponse(String uploadId, UploadStatus status) {
}
