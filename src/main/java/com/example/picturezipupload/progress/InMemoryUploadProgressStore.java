package com.example.picturezipupload.progress;

import com.example.picturezipupload.domain.UploadTaskProgress;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版进度存储。
 *
 * <p>适合本地开发和测试；生产多实例部署应使用 Redis 实现。</p>
 */
public class InMemoryUploadProgressStore implements UploadProgressStore {

    private final ConcurrentMap<String, UploadTaskProgress> progresses = new ConcurrentHashMap<>();

    @Override
    public void save(UploadTaskProgress progress) {
        progresses.put(progress.getUploadId(), progress);
    }

    @Override
    public Optional<UploadTaskProgress> get(String uploadId) {
        return Optional.ofNullable(progresses.get(uploadId));
    }

    @Override
    public void delete(String uploadId) {
        progresses.remove(uploadId);
    }
}
