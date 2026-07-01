package com.example.picturezipupload.progress;

import com.example.picturezipupload.domain.UploadTaskProgress;

import java.util.Optional;

/**
 * 上传进度存储抽象。
 *
 * <p>业务代码通过该接口读写进度，具体可落到内存或 Redis。</p>
 */
public interface UploadProgressStore {

    /**
     * 保存任务进度快照。
     */
    void save(UploadTaskProgress progress);

    /**
     * 查询任务进度。
     */
    Optional<UploadTaskProgress> get(String uploadId);

    /**
     * 删除任务进度。
     */
    void delete(String uploadId);
}
