package com.example.picturezipupload.domain;

import java.time.LocalDateTime;

public class UploadTaskProgress {

    private String uploadId;
    private String originalFilename;
    private UploadStatus status;
    private int totalChunks;
    private int uploadedChunks;
    private long processedFiles;
    private long inserted;
    private long duplicated;
    private long failed;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UploadTaskProgress created(String uploadId, String originalFilename, int totalChunks) {
        LocalDateTime now = LocalDateTime.now();
        UploadTaskProgress progress = new UploadTaskProgress();
        progress.setUploadId(uploadId);
        progress.setOriginalFilename(originalFilename);
        progress.setStatus(UploadStatus.CREATED);
        progress.setTotalChunks(totalChunks);
        progress.setCreatedAt(now);
        progress.setUpdatedAt(now);
        return progress;
    }

    public static UploadTaskProgress processing(String uploadId, String originalFilename) {
        UploadTaskProgress progress = created(uploadId, originalFilename, 0);
        progress.markProcessing();
        return progress;
    }

    public void markUploading() {
        setStatus(UploadStatus.UPLOADING);
        touch();
    }

    public void markMerging() {
        setStatus(UploadStatus.MERGING);
        touch();
    }

    public void markProcessing() {
        setStatus(UploadStatus.PROCESSING);
        touch();
    }

    public void markDone() {
        setStatus(UploadStatus.DONE);
        touch();
    }

    public void markFailed(String message) {
        setStatus(UploadStatus.FAILED);
        setMessage(message);
        touch();
    }

    public void recordChunkUploaded() {
        this.uploadedChunks++;
        markUploading();
    }

    public void recordInserted() {
        this.processedFiles++;
        this.inserted++;
        touch();
    }

    public void recordDuplicated() {
        this.processedFiles++;
        this.duplicated++;
        touch();
    }

    public void recordFailedFile() {
        this.processedFiles++;
        this.failed++;
        touch();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public void setStatus(UploadStatus status) {
        this.status = status;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getUploadedChunks() {
        return uploadedChunks;
    }

    public void setUploadedChunks(int uploadedChunks) {
        this.uploadedChunks = uploadedChunks;
    }

    public long getProcessedFiles() {
        return processedFiles;
    }

    public void setProcessedFiles(long processedFiles) {
        this.processedFiles = processedFiles;
    }

    public long getInserted() {
        return inserted;
    }

    public void setInserted(long inserted) {
        this.inserted = inserted;
    }

    public long getDuplicated() {
        return duplicated;
    }

    public void setDuplicated(long duplicated) {
        this.duplicated = duplicated;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
