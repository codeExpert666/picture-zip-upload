package com.example.picturezipupload.maintenance;

import com.example.picturezipupload.config.BusinessAreaTableResolver;
import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.mapper.CorpusAnalysisPictureMapper;
import com.example.picturezipupload.mapper.param.BackfillMetadataParam;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 基于 MyBatis 的维护脚本图片表操作。
 */
@Repository
public class MyBatisPictureMaintenanceRepository implements PictureMaintenanceRepository {

    private final CorpusAnalysisPictureMapper mapper;
    private final BusinessAreaTableResolver tableResolver;

    public MyBatisPictureMaintenanceRepository(CorpusAnalysisPictureMapper mapper,
                                               BusinessAreaTableResolver tableResolver) {
        this.mapper = mapper;
        this.tableResolver = tableResolver;
    }

    @Override
    public List<PictureRecord> findRecordsMissingMetadata(String businessArea, int limit) {
        return mapper.findRecordsMissingMetadata(tableResolver.resolve(businessArea), limit);
    }

    @Override
    public void updateBackfillMetadata(String businessArea, String voiceCode, String contentSha256, long fileSize,
                                       String uploadId, String originalZipName, String operator,
                                       LocalDateTime updateTime) {
        BackfillMetadataParam backfillMetadata = new BackfillMetadataParam();
        backfillMetadata.setContentSha256(contentSha256);
        backfillMetadata.setFileSize(fileSize);
        backfillMetadata.setUploadId(uploadId);
        backfillMetadata.setOriginalZipName(originalZipName);
        backfillMetadata.setOperator(operator);
        backfillMetadata.setUpdateTime(updateTime);
        mapper.updateBackfillMetadata(tableResolver.resolve(businessArea), voiceCode, backfillMetadata);
    }
}
