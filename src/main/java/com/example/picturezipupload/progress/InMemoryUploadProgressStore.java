package com.example.picturezipupload.progress;

import com.example.picturezipupload.domain.UploadTaskProgress;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
}
