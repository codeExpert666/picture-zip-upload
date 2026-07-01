package com.example.picturezipupload.config;

/**
 * 上传进度存储后端类型。
 */
public enum ProgressStoreType {
    /**
     * 进度仅保存在当前 JVM 内存中。
     */
    MEMORY,

    /**
     * 进度保存在 Redis 中，适合生产环境。
     */
    REDIS
}
