package com.example.picturezipupload.domain;

/**
 * 压缩包上传和后台导入任务状态。
 */
public enum UploadStatus {
    /**
     * 任务已创建，尚未上传分片。
     */
    CREATED,

    /**
     * 正在接收分片。
     */
    UPLOADING,

    /**
     * 分片已收齐，正在合并 zip。
     */
    MERGING,

    /**
     * zip 已合并，正在后台解压、判重和入库。
     */
    PROCESSING,

    /**
     * 后台导入完成。
     */
    DONE,

    /**
     * 上传或导入失败。
     */
    FAILED
}
