package com.example.picturezipupload.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 各业务领域图片表对应的图片记录。
 *
 * <p>原表的主键仍使用 {@code voice_code}，内容判重由 {@code contentSha256} 承担。</p>
 */
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
    private String operator;

    /**
     * 创建一条首次导入的新图片记录。
     *
     * <p>新图片默认进入待标注状态；重复图片不会走该工厂方法，而是更新既有记录的导入元数据。</p>
     */
    public static PictureRecord imported(String filename, String extname, String fileUrl, String filePath,
                                         String contentSha256, long fileSize, String uploadId,
                                         String originalZipName, String operator, LocalDateTime now) {
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
        record.setOperator(operator);
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

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
