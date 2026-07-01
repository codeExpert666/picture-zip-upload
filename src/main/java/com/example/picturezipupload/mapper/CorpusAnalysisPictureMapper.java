package com.example.picturezipupload.mapper;

import com.example.picturezipupload.domain.PictureRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
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
    @Select("""
            SELECT
                voice_code AS voiceCode,
                filename,
                extname,
                file_URL AS fileUrl,
                import_time AS importTime,
                update_time AS updateTime,
                file_path AS filePath,
                status,
                content_sha256 AS contentSha256,
                file_size AS fileSize,
                upload_id AS uploadId,
                original_zip_name AS originalZipName,
                operator
            FROM ${tableName}
            WHERE content_sha256 = #{contentSha256}
            LIMIT 1
            """)
    Optional<PictureRecord> findByContentSha256(@Param("tableName") String tableName,
                                                @Param("contentSha256") String contentSha256);

    /**
     * 插入首次导入的图片记录。
     */
    @Insert("""
            INSERT INTO ${tableName} (
                voice_code,
                filename,
                extname,
                file_URL,
                import_time,
                update_time,
                file_path,
                status,
                content_sha256,
                file_size,
                upload_id,
                original_zip_name,
                operator
            ) VALUES (
                #{record.voiceCode},
                #{record.filename},
                #{record.extname},
                #{record.fileUrl},
                #{record.importTime},
                #{record.updateTime},
                #{record.filePath},
                #{record.status},
                #{record.contentSha256},
                #{record.fileSize},
                #{record.uploadId},
                #{record.originalZipName},
                #{record.operator}
            )
            """)
    void insert(@Param("tableName") String tableName, @Param("record") PictureRecord record);

    /**
     * 记录重复图片的最新导入信息。
     *
     * <p>这里刻意不更新状态、物理路径和首次导入时间，避免覆盖既有标注流程。</p>
     */
    @Update("""
            UPDATE ${tableName}
            SET
                filename = #{filename},
                extname = #{extname},
                update_time = #{updateTime},
                upload_id = #{uploadId},
                original_zip_name = #{originalZipName},
                operator = #{operator}
            WHERE content_sha256 = #{contentSha256}
            """)
    void updateDuplicateImport(@Param("tableName") String tableName,
                               @Param("contentSha256") String contentSha256,
                               @Param("filename") String filename,
                               @Param("extname") String extname,
                               @Param("uploadId") String uploadId,
                               @Param("originalZipName") String originalZipName,
                               @Param("operator") String operator,
                               @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查询缺少新增元数据的历史记录，用于维护脚本回填。
     */
    @Select("""
            SELECT
                voice_code AS voiceCode,
                filename,
                extname,
                file_URL AS fileUrl,
                import_time AS importTime,
                update_time AS updateTime,
                file_path AS filePath,
                status,
                content_sha256 AS contentSha256,
                file_size AS fileSize,
                upload_id AS uploadId,
                original_zip_name AS originalZipName,
                operator
            FROM ${tableName}
            WHERE content_sha256 IS NULL
               OR file_size IS NULL
               OR file_size = 0
            ORDER BY import_time ASC, voice_code ASC
            LIMIT #{limit}
            """)
    List<PictureRecord> findRecordsMissingMetadata(@Param("tableName") String tableName,
                                                   @Param("limit") int limit);

    /**
     * 回填历史记录的内容哈希和文件大小。
     */
    @Update("""
            UPDATE ${tableName}
            SET
                content_sha256 = #{contentSha256},
                file_size = #{fileSize},
                upload_id = #{uploadId},
                original_zip_name = #{originalZipName},
                operator = #{operator},
                update_time = #{updateTime}
            WHERE voice_code = #{voiceCode}
            """)
    void updateBackfillMetadata(@Param("tableName") String tableName,
                                @Param("voiceCode") String voiceCode,
                                @Param("contentSha256") String contentSha256,
                                @Param("fileSize") long fileSize,
                                @Param("uploadId") String uploadId,
                                @Param("originalZipName") String originalZipName,
                                @Param("operator") String operator,
                                @Param("updateTime") LocalDateTime updateTime);
}
