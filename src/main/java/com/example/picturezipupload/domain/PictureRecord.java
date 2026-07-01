package com.example.picturezipupload.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class PictureRecord {

    private String voiceCode;
    private String filename;
    private String extname;
    private String fileUrl;
    private LocalDateTime importTime;
    private LocalDateTime updateTime;
    private String filePath;
    private String status;
    private String contentSha256;
    private long fileSize;
    private String uploadId;
    private String originalZipName;

    public static PictureRecord imported(String filename, String extname, String fileUrl, String filePath,
                                         String contentSha256, long fileSize, String uploadId,
                                         String originalZipName, LocalDateTime now) {
        PictureRecord record = new PictureRecord();
        record.setVoiceCode(UUID.randomUUID().toString());
        record.setFilename(filename);
        record.setExtname(extname);
        record.setFileUrl(fileUrl);
        record.setImportTime(now);
        record.setUpdateTime(now);
        record.setFilePath(filePath);
        record.setStatus(PictureStatus.MARK.name());
        record.setContentSha256(contentSha256);
        record.setFileSize(fileSize);
        record.setUploadId(uploadId);
        record.setOriginalZipName(originalZipName);
        return record;
    }

    public String getVoiceCode() {
        return voiceCode;
    }

    public void setVoiceCode(String voiceCode) {
        this.voiceCode = voiceCode;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getExtname() {
        return extname;
    }

    public void setExtname(String extname) {
        this.extname = extname;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public LocalDateTime getImportTime() {
        return importTime;
    }

    public void setImportTime(LocalDateTime importTime) {
        this.importTime = importTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public void setContentSha256(String contentSha256) {
        this.contentSha256 = contentSha256;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getOriginalZipName() {
        return originalZipName;
    }

    public void setOriginalZipName(String originalZipName) {
        this.originalZipName = originalZipName;
    }
}
