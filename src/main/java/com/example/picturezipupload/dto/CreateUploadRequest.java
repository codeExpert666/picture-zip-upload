package com.example.picturezipupload.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建压缩包上传任务的请求参数。
 */
public class CreateUploadRequest {

    /**
     * 用户上传的原始 zip 文件名，用于进度展示和导入记录追溯。
     */
    @NotBlank(message = "原始文件名不能为空")
    private String originalFilename;

    /**
     * 前端切分出的分片总数，必须至少包含一个分片。
     */
    @Min(value = 1, message = "总分片数必须大于等于 1")
    private int totalChunks;

    /**
     * 原始 zip 文件总字节数，用于前端展示和服务端基础校验。
     */
    @Positive(message = "文件总大小必须大于 0")
    private long totalSize;

    /**
     * 图片所属业务领域，服务端会根据该值路由到对应业务图片表。
     */
    @NotBlank(message = "业务领域不能为空")
    @Size(max = 50, message = "业务领域长度不能超过 50 个字符")
    private String businessArea;

    /**
     * 发起上传的操作人，用于导入审计和重复图片更新记录。
     */
    @NotBlank(message = "操作人不能为空")
    @Size(max = 50, message = "操作人长度不能超过 50 个字符")
    private String operator;

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
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
}
