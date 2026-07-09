package com.example.picturezipupload.maintenance;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 图片维护脚本参数。
 */
@ConfigurationProperties(prefix = "picture-maintenance")
public class PictureMaintenanceProperties {

    /**
     * 默认关闭，避免普通应用启动时误触发维护任务。
     */
    private boolean enabled;

    /**
     * 本次维护任务模式。
     */
    private PictureMaintenanceMode mode;

    /**
     * 默认 dry-run，只统计和校验，不写数据库。
     */
    private boolean dryRun = true;

    /**
     * 业务领域编码，会通过白名单解析到具体图片表。
     */
    private String businessArea;

    /**
     * 新目录导入模式下的扫描目录；未配置时使用 {@code picture-upload.image-root-path}。
     */
    private Path sourceRoot;

    /**
     * 旧记录回填模式下按文件名兜底查找的历史图片根目录。
     */
    private Path legacyRoot;

    /**
     * 新目录导入模式下写入 {@code file_URL} 的 URL 前缀；未配置时使用 {@code picture-upload.public-url-prefix}。
     */
    private String publicUrlPrefix;

    /**
     * 本次维护操作人，写入 operator 字段便于审计。
     */
    private String operator;

    /**
     * 本次维护批次号，写入 upload_id 字段便于追踪。
     */
    private String batchId;

    /**
     * 旧记录回填每次最多读取的记录数。
     */
    private int limit = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public PictureMaintenanceMode getMode() {
        return mode;
    }

    public void setMode(PictureMaintenanceMode mode) {
        this.mode = mode;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getBusinessArea() {
        return businessArea;
    }

    public void setBusinessArea(String businessArea) {
        this.businessArea = businessArea;
    }

    public Path getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public Path getLegacyRoot() {
        return legacyRoot;
    }

    public void setLegacyRoot(Path legacyRoot) {
        this.legacyRoot = legacyRoot;
    }

    public String getPublicUrlPrefix() {
        return publicUrlPrefix;
    }

    public void setPublicUrlPrefix(String publicUrlPrefix) {
        this.publicUrlPrefix = publicUrlPrefix;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
