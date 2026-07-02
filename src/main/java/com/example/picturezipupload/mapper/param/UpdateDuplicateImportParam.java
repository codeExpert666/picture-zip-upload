package com.example.picturezipupload.mapper.param;

import java.time.LocalDateTime;

/**
 * 重复图片导入更新的 Mapper 参数对象。
 */
public class UpdateDuplicateImportParam {

    private String tableName;
    private String contentSha256;
    private String filename;
    private String extname;
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

    public String getContentSha256() {
        return contentSha256;
    }

    public void setContentSha256(String contentSha256) {
        this.contentSha256 = contentSha256;
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
