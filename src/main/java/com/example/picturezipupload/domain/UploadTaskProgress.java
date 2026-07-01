package com.example.picturezipupload.domain;

import java.time.LocalDateTime;

/**
 * 上传任务进度快照。
 *
 * <p>同一个对象同时表达分片上传进度和后台导入统计，便于前端轮询一个接口即可展示完整流程。</p>
 */
public class UploadTaskProgress {

    private String uploadId;
    private String originalFilename;
    private String businessArea;
    private String operator;
    private UploadStatus status;
    private int totalChunks;
    private int uploadedChunks;
    private long totalFiles;
    private long processedFiles;
    private long inserted;
    private long duplicated;
    private long failed;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建一个等待上传分片的任务进度。
     */
    public static UploadTaskProgress created(String uploadId, String originalFilename, int totalChunks) {
        return created(uploadId, originalFilename, totalChunks, null, null);
    }

    public static UploadTaskProgress created(String uploadId, String originalFilename, int totalChunks,
                                             String businessArea, String operator) {
        LocalDateTime now = LocalDateTime.now();
        UploadTaskProgress progress = new UploadTaskProgress();
        progress.setUploadId(uploadId);
        progress.setOriginalFilename(originalFilename);
        progress.setBusinessArea(businessArea);
        progress.setOperator(operator);
        progress.setStatus(UploadStatus.CREATED);
        progress.setTotalChunks(totalChunks);
        progress.setCreatedAt(now);
        progress.setUpdatedAt(now);
        return progress;
    }

    public static UploadTaskProgress processing(String uploadId, String originalFilename) {
        return processing(uploadId, originalFilename, null, null);
    }

    public static UploadTaskProgress processing(String uploadId, String originalFilename,
                                                String businessArea, String operator) {
        UploadTaskProgress progress = created(uploadId, originalFilename, 0, businessArea, operator);
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

    /**
     * 记录已接收一个分片。
     */
    public void recordChunkUploaded() {
        this.uploadedChunks++;
        markUploading();
    }

    /**
     * 按已落盘分片数量刷新上传进度，避免同一分片重试时重复计数。
     */
    public void recordUploadedChunks(int uploadedChunks) {
        if (uploadedChunks < 0) {
            throw new IllegalArgumentException("已上传分片数不能小于 0");
        }
        this.uploadedChunks = uploadedChunks;
        markUploading();
    }

    /**
     * 记录一张新图片已入库。
     */
    public void recordInserted() {
        this.processedFiles++;
        this.inserted++;
        touch();
    }

    /**
     * 记录一张重复图片已按既有记录处理。
     */
    public void recordDuplicated() {
        this.processedFiles++;
        this.duplicated++;
        touch();
    }

    /**
     * 记录一个 zip 条目被过滤或处理失败。
     */
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

    public String getBusinessArea() {
        return businessArea;
    }

    public void setBusinessArea(String businessArea) {
        this.businessArea = businessArea;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
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

    public long getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(long totalFiles) {
        if (totalFiles < 0) {
            throw new IllegalArgumentException("导入文件总数不能小于 0");
        }
        this.totalFiles = totalFiles;
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
