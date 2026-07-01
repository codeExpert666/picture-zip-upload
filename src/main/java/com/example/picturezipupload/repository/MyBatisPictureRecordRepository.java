package com.example.picturezipupload.repository;

import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.mapper.CorpusAnalysisPictureMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MyBatisPictureRecordRepository implements PictureRecordRepository {

    private final CorpusAnalysisPictureMapper mapper;

    public MyBatisPictureRecordRepository(CorpusAnalysisPictureMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PictureRecord> findByContentSha256(String contentSha256) {
        return mapper.findByContentSha256(contentSha256);
    }

    @Override
    public void insert(PictureRecord record) {
        mapper.insert(record);
    }

    @Override
    public void updateDuplicateImport(String contentSha256, String filename, String extname,
                                      String uploadId, String originalZipName, LocalDateTime updateTime) {
        mapper.updateDuplicateImport(contentSha256, filename, extname, uploadId, originalZipName, updateTime);
    }
}
