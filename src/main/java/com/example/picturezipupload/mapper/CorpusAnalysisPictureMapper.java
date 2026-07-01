package com.example.picturezipupload.mapper;

import com.example.picturezipupload.domain.PictureRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CorpusAnalysisPictureMapper {

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
