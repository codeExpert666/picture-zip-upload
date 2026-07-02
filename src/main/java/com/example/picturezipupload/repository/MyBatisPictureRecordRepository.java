package com.example.picturezipupload.repository;

import com.example.picturezipupload.config.BusinessAreaTableResolver;
import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.mapper.CorpusAnalysisPictureMapper;
import com.example.picturezipupload.mapper.param.DuplicateImportMetadataParam;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 基于 MyBatis 的图片记录仓储实现。
 */
@Repository
public class MyBatisPictureRecordRepository implements PictureRecordRepository {

    private final CorpusAnalysisPictureMapper mapper;
    private final BusinessAreaTableResolver tableResolver;

    public MyBatisPictureRecordRepository(CorpusAnalysisPictureMapper mapper, BusinessAreaTableResolver tableResolver) {
        this.mapper = mapper;
        this.tableResolver = tableResolver;
    }

    @Override
    public Optional<PictureRecord> findByContentSha256(String businessArea, String contentSha256) {
        return mapper.findByContentSha256(tableResolver.resolve(businessArea), contentSha256);
    }

    @Override
    public void insert(String businessArea, PictureRecord record) {
        mapper.insert(tableResolver.resolve(businessArea), record);
    }

    @Override
    public void updateDuplicateImport(String businessArea, String contentSha256, String filename, String extname,
                                      String uploadId, String originalZipName, String operator, LocalDateTime updateTime) {
        DuplicateImportMetadataParam metadata = new DuplicateImportMetadataParam();
        metadata.setFilename(filename);
        metadata.setExtname(extname);
        metadata.setUploadId(uploadId);
        metadata.setOriginalZipName(originalZipName);
        metadata.setOperator(operator);
        metadata.setUpdateTime(updateTime);
        mapper.updateDuplicateImport(tableResolver.resolve(businessArea), contentSha256, metadata);
    }
}
