package com.example.picturezipupload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片上传模块配置。
 *
 * <p>分片/zip/tmp 使用工作目录，正式图片使用独立图片根目录，便于把服务器直放图片目录作为后续接口上传的统一图片根。</p>
 */
@ConfigurationProperties(prefix = "picture-upload")
public class PictureUploadProperties {

    /**
     * 上传工作目录，下面会分出 chunks、zips、tmp 三类临时或中间目录。
     */
    private Path workRootPath = Paths.get(System.getProperty("java.io.tmpdir"), "picture-upload");

    /**
     * 正式图片存储根目录。数据组直接放到服务器的新图片目录也应配置为该目录。
     */
    private Path imageRootPath = Paths.get("/data/pictures");

    /**
     * 图片对外访问 URL 前缀，应与静态资源映射保持一致。
     */
    private String publicUrlPrefix = "/api/pictures/files";

    /**
     * 上传进度存储类型；本地样例默认内存，公司环境建议切换为 Redis。
     */
    private ProgressStoreType progressStore = ProgressStoreType.MEMORY;

    /**
     * 后台导入线程池核心线程数。
     */
    private int importCorePoolSize = 2;

    /**
     * 后台导入线程池最大线程数。
     */
    private int importMaxPoolSize = 4;

    /**
     * 后台导入任务队列长度。
     */
    private int importQueueCapacity = 64;

    /**
     * 文件复制和解压时使用的缓冲区大小，默认 1MB，避免单次读写占用过多堆内存。
     */
    private int ioBufferSize = 1024 * 1024;

    /**
     * 业务领域到图片表名的白名单映射。
     */
    private Map<String, String> businessAreaTables = new LinkedHashMap<>();

    /**
     * 旧图片静态资源目录，用于保留历史 file_URL 可访问性。
     */
    private Map<String, StaticLocation> legacyStaticLocations = new LinkedHashMap<>();

    public Path getWorkRootPath() {
        return workRootPath;
    }

    public void setWorkRootPath(Path workRootPath) {
        this.workRootPath = workRootPath;
    }

    public Path getImageRootPath() {
        return imageRootPath;
    }

    public void setImageRootPath(Path imageRootPath) {
        this.imageRootPath = imageRootPath;
    }

    public String getPublicUrlPrefix() {
        return publicUrlPrefix;
    }

    public void setPublicUrlPrefix(String publicUrlPrefix) {
        this.publicUrlPrefix = trimTrailingSlash(publicUrlPrefix);
    }

    public ProgressStoreType getProgressStore() {
        return progressStore;
    }

    public void setProgressStore(ProgressStoreType progressStore) {
        this.progressStore = progressStore;
    }

    public int getImportCorePoolSize() {
        return importCorePoolSize;
    }

    public void setImportCorePoolSize(int importCorePoolSize) {
        this.importCorePoolSize = importCorePoolSize;
    }

    public int getImportMaxPoolSize() {
        return importMaxPoolSize;
    }

    public void setImportMaxPoolSize(int importMaxPoolSize) {
        this.importMaxPoolSize = importMaxPoolSize;
    }

    public int getImportQueueCapacity() {
        return importQueueCapacity;
    }

    public void setImportQueueCapacity(int importQueueCapacity) {
        this.importQueueCapacity = importQueueCapacity;
    }

    public int getIoBufferSize() {
        return ioBufferSize;
    }

    public void setIoBufferSize(int ioBufferSize) {
        this.ioBufferSize = ioBufferSize;
    }

    public Map<String, String> getBusinessAreaTables() {
        return businessAreaTables;
    }

    public void setBusinessAreaTables(Map<String, String> businessAreaTables) {
        this.businessAreaTables = businessAreaTables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(businessAreaTables);
    }

    public Map<String, StaticLocation> getLegacyStaticLocations() {
        return legacyStaticLocations;
    }

    public void setLegacyStaticLocations(Map<String, StaticLocation> legacyStaticLocations) {
        this.legacyStaticLocations = legacyStaticLocations == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(legacyStaticLocations);
    }

    public Path chunksPath() {
        return workRootPath.resolve("chunks");
    }

    /**
     * 合并后的原始 zip 存储目录。
     */
    public Path zipsPath() {
        return workRootPath.resolve("zips");
    }

    /**
     * 去重后的图片正式存储根目录。
     */
    public Path imagesPath() {
        return imageRootPath;
    }

    /**
     * 解压单张图片时的临时文件目录。
     */
    public Path tempPath() {
        return workRootPath.resolve("tmp");
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "/api/pictures/files";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 一个静态资源 URL 前缀和本地目录的映射。
     */
    public static class StaticLocation {

        private Path rootPath;

        private String publicUrlPrefix;

        public Path getRootPath() {
            return rootPath;
        }

        public void setRootPath(Path rootPath) {
            this.rootPath = rootPath;
        }

        public String getPublicUrlPrefix() {
            return publicUrlPrefix;
        }

        public void setPublicUrlPrefix(String publicUrlPrefix) {
            if (publicUrlPrefix == null || publicUrlPrefix.isBlank()) {
                this.publicUrlPrefix = null;
                return;
            }
            this.publicUrlPrefix = trimTrailingSlash(publicUrlPrefix);
        }
    }
}
