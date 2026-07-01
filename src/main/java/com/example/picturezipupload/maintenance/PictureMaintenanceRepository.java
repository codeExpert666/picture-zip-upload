package com.example.picturezipupload.maintenance;

import com.example.picturezipupload.domain.PictureRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 维护脚本专用图片表操作。
 */
public interface PictureMaintenanceRepository {

    /**
     * 分批查询缺少内容哈希或文件大小的历史记录。
     */
    List<PictureRecord> findRecordsMissingMetadata(String businessArea, int limit);

    /**
     * 只回填新增元数据和本次维护批次信息，不修改标注状态和历史访问路径。
     */
    void updateBackfillMetadata(String businessArea, String voiceCode, String contentSha256, long fileSize,
                                String uploadId, String originalZipName, String operator, LocalDateTime updateTime);
}
