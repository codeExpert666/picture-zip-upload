package com.example.picturezipupload.mapper.param;

import java.time.LocalDateTime;

/**
 * 历史图片元数据回填更新的 Mapper 参数对象。
 */
public class UpdateBackfillMetadataParam {

    private String tableName;
    private String voiceCode;
    private String contentSha256;
    private long fileSize;
    private String uploadId;
    private String originalZipName;
    private String operator;
    private LocalDateTime updateTime;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getVoiceCode() {
        return voiceCode;
    }

    public void setVoiceCode(String voiceCode) {
        this.voiceCode = voiceCode;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
