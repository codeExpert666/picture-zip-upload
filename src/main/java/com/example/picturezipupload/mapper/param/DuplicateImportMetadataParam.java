package com.example.picturezipupload.mapper.param;

import java.time.LocalDateTime;

/**
 * 重复图片导入时需要写回的最新导入信息。
 */
public class DuplicateImportMetadataParam {

    private String filename;
    private String extname;
    private String uploadId;
    private String originalZipName;
    private String operator;
    private LocalDateTime updateTime;

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
