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
     * 旧记录回填单次执行最多处理的记录数。
     */
    private int limit = 1000;

    /**
     * 旧记录回填每次按主键游标查询的记录数。
     */
    private int batchSize = 1000;

    /**
     * 旧记录回填每处理多少条记录输出进度并保存一次检查点。
     */
    private int progressInterval = 100;

    /**
     * 新任务首次执行时跳过的最后一个 {@code voice_code}。
     */
    private String afterVoiceCode;

    /**
     * 旧记录回填检查点文件；未配置时写入上传工作目录的 maintenance 子目录。
     */
    private Path checkpointFile;

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

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getProgressInterval() {
        return progressInterval;
    }

    public void setProgressInterval(int progressInterval) {
        this.progressInterval = progressInterval;
    }

    public String getAfterVoiceCode() {
        return afterVoiceCode;
    }

    public void setAfterVoiceCode(String afterVoiceCode) {
        this.afterVoiceCode = afterVoiceCode;
    }

    public Path getCheckpointFile() {
        return checkpointFile;
    }

    public void setCheckpointFile(Path checkpointFile) {
        this.checkpointFile = checkpointFile;
    }
}
