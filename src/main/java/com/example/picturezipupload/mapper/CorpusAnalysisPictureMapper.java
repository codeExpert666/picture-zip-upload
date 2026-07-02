package com.example.picturezipupload.mapper;

import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.mapper.param.UpdateBackfillMetadataParam;
import com.example.picturezipupload.mapper.param.UpdateDuplicateImportParam;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 图片业务表 MyBatis Mapper。
 *
 * <p>表字段命名沿用存量库，其中 {@code file_URL} 通过别名映射到 Java 字段 {@code fileUrl}。</p>
 */
public interface CorpusAnalysisPictureMapper {

    /**
     * 根据内容哈希查询图片记录，用于导入前判重。
     */
    Optional<PictureRecord> findByContentSha256(@Param("tableName") String tableName,
                                                @Param("contentSha256") String contentSha256);

    /**
     * 插入首次导入的图片记录。
     */
    void insert(@Param("tableName") String tableName, @Param("record") PictureRecord record);

    /**
     * 记录重复图片的最新导入信息。
     *
     * <p>这里刻意不更新状态、物理路径和首次导入时间，避免覆盖既有标注流程。</p>
     */
    void updateDuplicateImport(UpdateDuplicateImportParam param);

    /**
     * 查询缺少新增元数据的历史记录，用于维护脚本回填。
     */
    List<PictureRecord> findRecordsMissingMetadata(@Param("tableName") String tableName,
                                                   @Param("limit") int limit);

    /**
     * 回填历史记录的内容哈希和文件大小。
     */
    void updateBackfillMetadata(UpdateBackfillMetadataParam param);
}
