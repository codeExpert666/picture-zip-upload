package com.example.picturezipupload.mapper;

import com.example.picturezipupload.domain.PictureRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * {@code corpus_analysis_picture} 表 MyBatis Mapper。
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
                original_zip_name AS originalZipName
            FROM corpus_analysis_picture
            WHERE content_sha256 = #{contentSha256}
            LIMIT 1
            """)
    Optional<PictureRecord> findByContentSha256(@Param("contentSha256") String contentSha256);

    /**
     * 插入首次导入的图片记录。
     */
    @Insert("""
            INSERT INTO corpus_analysis_picture (
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
                original_zip_name
            ) VALUES (
                #{voiceCode},
                #{filename},
                #{extname},
                #{fileUrl},
                #{importTime},
                #{updateTime},
                #{filePath},
                #{status},
                #{contentSha256},
                #{fileSize},
                #{uploadId},
                #{originalZipName}
            )
            """)
    void insert(PictureRecord record);

    /**
     * 记录重复图片的最新导入信息。
     *
     * <p>这里刻意不更新状态、物理路径和首次导入时间，避免覆盖既有标注流程。</p>
     */
    @Update("""
            UPDATE corpus_analysis_picture
            SET
                filename = #{filename},
                extname = #{extname},
                update_time = #{updateTime},
                upload_id = #{uploadId},
                original_zip_name = #{originalZipName}
            WHERE content_sha256 = #{contentSha256}
            """)
    void updateDuplicateImport(@Param("contentSha256") String contentSha256,
                               @Param("filename") String filename,
                               @Param("extname") String extname,
                               @Param("uploadId") String uploadId,
                               @Param("originalZipName") String originalZipName,
                               @Param("updateTime") LocalDateTime updateTime);
}
