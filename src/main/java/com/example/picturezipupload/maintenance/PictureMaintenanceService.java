package com.example.picturezipupload.maintenance;

import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.repository.PictureRecordRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 图片维护脚本服务。
 */
@Service
public class PictureMaintenanceService {

    private final PictureRecordRepository pictureRepository;
    private final PictureMaintenanceRepository maintenanceRepository;
    private final PictureFileInspector fileInspector;
    private final StaticPicturePathResolver pathResolver;

    public PictureMaintenanceService(PictureRecordRepository pictureRepository,
                                     PictureMaintenanceRepository maintenanceRepository,
                                     PictureFileInspector fileInspector,
                                     StaticPicturePathResolver pathResolver) {
        this.pictureRepository = pictureRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.fileInspector = fileInspector;
        this.pathResolver = pathResolver;
    }

    /**
     * 递归扫描服务器目录，将图片按原地引用方式写入业务表。
     *
     * <p>该流程不会复制、移动或软链图片文件；数据库中的 {@code file_path} 指向原文件，
     * {@code file_URL} 由配置的静态资源前缀和相对路径生成。</p>
     */
    public PictureMaintenanceReport importDirectDirectory(String businessArea, Path sourceRoot, String publicUrlPrefix,
                                                          String operator, String batchId, boolean dryRun)
            throws IOException {
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        PictureMaintenanceReport report = new PictureMaintenanceReport();
        try (Stream<Path> paths = Files.walk(normalizedRoot)) {
            Iterator<Path> iterator = paths.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                report.recordScanned();
                Optional<PictureFileMetadata> metadata = fileInspector.inspectImage(path);
                if (metadata.isEmpty()) {
                    report.recordInvalid();
                    continue;
                }
                if (pictureRepository.findByContentSha256(businessArea, metadata.get().contentSha256()).isPresent()) {
                    report.recordDuplicated();
                    continue;
                }
                if (!dryRun) {
                    PictureRecord record = PictureRecord.imported(
                            metadata.get().filename(),
                            metadata.get().extname(),
                            pathResolver.fileUrlFor(path, normalizedRoot, publicUrlPrefix),
                            path.toAbsolutePath().normalize().toString(),
                            metadata.get().contentSha256(),
                            metadata.get().fileSize(),
                            batchId,
                            "DIRECT:" + normalizedRoot,
                            operator,
                            LocalDateTime.now());
                    try {
                        pictureRepository.insert(businessArea, record);
                    } catch (DuplicateKeyException ex) {
                        report.recordDuplicated();
                        continue;
                    }
                }
                report.recordInserted();
            }
        }
        return report;
    }

    /**
     * 回填历史记录新增字段。
     *
     * <p>定位文件时优先使用历史 {@code file_path}，其次用 {@code file_URL} 反解，最后才按
     * {@code legacyRoot + filename/extname} 做唯一匹配；回填过程不修改标注状态。</p>
     */
    public PictureMaintenanceReport backfillExistingRecords(String businessArea, Path legacyRoot, String operator,
                                                            String batchId, int limit, boolean dryRun)
            throws IOException {
        PictureMaintenanceReport report = new PictureMaintenanceReport();
        for (PictureRecord record : maintenanceRepository.findRecordsMissingMetadata(businessArea, limit)) {
            report.recordScanned();
            Optional<Path> path = resolveExistingRecordPath(record, legacyRoot);
            if (path.isEmpty() || !Files.isRegularFile(path.get())) {
                report.recordMissing();
                continue;
            }
            Optional<PictureFileMetadata> metadata = fileInspector.inspectImage(path.get());
            if (metadata.isEmpty()) {
                report.recordInvalid();
                continue;
            }
            Optional<PictureRecord> existing = pictureRepository.findByContentSha256(
                    businessArea, metadata.get().contentSha256());
            if (existing.isPresent() && !record.getVoiceCode().equals(existing.get().getVoiceCode())) {
                report.recordConflicted();
                continue;
            }
            if (!dryRun) {
                maintenanceRepository.updateBackfillMetadata(
                        businessArea,
                        record.getVoiceCode(),
                        metadata.get().contentSha256(),
                        metadata.get().fileSize(),
                        batchId,
                        "LEGACY_BACKFILL",
                        operator,
                        LocalDateTime.now());
            }
            report.recordBackfilled();
        }
        return report;
    }

    private Optional<Path> resolveExistingRecordPath(PictureRecord record, Path legacyRoot) throws IOException {
        if (record.getFilePath() != null && !record.getFilePath().isBlank()) {
            Path filePath = Path.of(record.getFilePath()).toAbsolutePath().normalize();
            if (Files.isRegularFile(filePath)) {
                return Optional.of(filePath);
            }
        }
        Optional<Path> pathFromUrl = pathResolver.resolveFileUrl(record.getFileUrl());
        if (pathFromUrl.isPresent() && Files.isRegularFile(pathFromUrl.get())) {
            return pathFromUrl;
        }
        if (legacyRoot == null || record.getFilename() == null || record.getExtname() == null) {
            return Optional.empty();
        }
        String targetName = record.getFilename() + "." + record.getExtname();
        try (Stream<Path> paths = Files.walk(legacyRoot.toAbsolutePath().normalize())) {
            // 兜底匹配必须唯一，避免同名图片散落在多级目录时误回填。
            java.util.List<Path> matches = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(targetName))
                    .limit(2)
                    .toList();
            return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
        }
    }
}
