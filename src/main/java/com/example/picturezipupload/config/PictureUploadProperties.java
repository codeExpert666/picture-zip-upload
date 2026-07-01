package com.example.picturezipupload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "picture-upload")
public class PictureUploadProperties {

    private Path rootPath = Paths.get(System.getProperty("java.io.tmpdir"), "picture-upload");

    private String publicUrlPrefix = "/api/pictures/files";

    private ProgressStoreType progressStore = ProgressStoreType.MEMORY;

    private int importCorePoolSize = 2;

    private int importMaxPoolSize = 4;

    private int importQueueCapacity = 64;

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

    public Path zipsPath() {
        return rootPath.resolve("zips");
    }

    public Path imagesPath() {
        return rootPath.resolve("images");
    }

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
