package com.example.picturezipupload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 图片上传模块配置。
 *
 * <p>路径配置统一从根目录派生，便于将本地文件系统替换为挂载盘或对象存储适配层。</p>
 */
@ConfigurationProperties(prefix = "picture-upload")
public class PictureUploadProperties {

    /**
     * 上传模块根目录，下面会分出 chunks、zips、images、tmp 四类子目录。
     */
    private Path rootPath = Paths.get(System.getProperty("java.io.tmpdir"), "picture-upload");

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

    public Path chunksPath() {
        return rootPath.resolve("chunks");
    }

    /**
     * 合并后的原始 zip 存储目录。
     */
    public Path zipsPath() {
        return rootPath.resolve("zips");
    }

    /**
     * 去重后的图片正式存储目录。
     */
    public Path imagesPath() {
        return rootPath.resolve("images");
    }

    /**
     * 解压单张图片时的临时文件目录。
     */
    public Path tempPath() {
        return rootPath.resolve("tmp");
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
}
