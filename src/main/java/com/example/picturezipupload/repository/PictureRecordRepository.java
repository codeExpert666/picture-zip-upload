package com.example.picturezipupload.repository;

import com.example.picturezipupload.domain.PictureRecord;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 图片记录仓储接口。
 *
 * <p>导入服务依赖该接口而不是 MyBatis，便于单元测试中替换为内存实现。</p>
 */
public interface PictureRecordRepository {

    /**
     * 按内容 SHA-256 查询图片记录。
     */
    Optional<PictureRecord> findByContentSha256(String businessArea, String contentSha256);

    /**
     * 插入首次出现的图片记录。
     */
    void insert(String businessArea, PictureRecord record);

    /**
     * 更新重复导入图片的元数据，不改变图片标注状态。
     */
    void updateDuplicateImport(String businessArea, String contentSha256, String filename, String extname,
                               String uploadId, String originalZipName, String operator, LocalDateTime updateTime);
}
