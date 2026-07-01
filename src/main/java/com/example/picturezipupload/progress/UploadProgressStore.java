package com.example.picturezipupload.progress;

import com.example.picturezipupload.domain.UploadTaskProgress;

import java.util.Optional;

public interface UploadProgressStore {

    void save(UploadTaskProgress progress);

    Optional<UploadTaskProgress> get(String uploadId);
}
