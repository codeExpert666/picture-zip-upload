package com.example.picturezipupload.repository;

import com.example.picturezipupload.domain.PictureRecord;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PictureRecordRepository {

    Optional<PictureRecord> findByContentSha256(String contentSha256);

    void insert(PictureRecord record);

    void updateDuplicateImport(String contentSha256, String filename, String extname,
                               String uploadId, String originalZipName, LocalDateTime updateTime);
}
