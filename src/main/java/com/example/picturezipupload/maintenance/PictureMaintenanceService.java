package com.example.picturezipupload.maintenance;

import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.repository.PictureRecordRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
     * <p>定位文件时只信任历史 {@code file_path}，无效路径计入 missing；回填过程按
     * {@code voice_code} 游标单线程推进，不修改标注状态。</p>
     */
    public PictureBackfillResult backfillExistingRecords(PictureBackfillRequest request,
                                                         PictureBackfillProgressListener progressListener)
            throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(progressListener, "progressListener");
        PictureMaintenanceReport report = new PictureMaintenanceReport();
        String cursor = request.afterVoiceCode();
        int processed = 0;
        int lastReported = 0;
        boolean exhausted = false;

        while (processed < request.limit()) {
            int queryLimit = Math.min(request.batchSize(), request.limit() - processed);
            List<PictureRecord> records = maintenanceRepository.findRecordsMissingMetadata(
                    request.businessArea(), cursor, queryLimit);
            if (records.isEmpty()) {
                exhausted = true;
                break;
            }
            for (PictureRecord record : records) {
                processBackfillRecord(request, record, report);
                processed++;
                cursor = record.getVoiceCode();
                if (processed - lastReported >= request.progressInterval()) {
                    progressListener.onProgress(new PictureBackfillProgress(processed, cursor, report));
                    lastReported = processed;
                }
            }
            if (processed > lastReported) {
                progressListener.onProgress(new PictureBackfillProgress(processed, cursor, report));
                lastReported = processed;
            }
            if (records.size() < queryLimit) {
                exhausted = true;
                break;
            }
        }
        if (!exhausted && processed == request.limit()) {
            exhausted = maintenanceRepository.findRecordsMissingMetadata(request.businessArea(), cursor, 1).isEmpty();
        }
        return new PictureBackfillResult(report, cursor, exhausted);
    }

    private void processBackfillRecord(PictureBackfillRequest request, PictureRecord record,
                                       PictureMaintenanceReport report) throws IOException {
        report.recordScanned();
        Optional<Path> path = resolveExistingRecordPath(record);
        if (path.isEmpty()) {
            report.recordMissing();
            return;
        }
        Optional<PictureFileMetadata> metadata = fileInspector.inspectImage(path.get());
        if (metadata.isEmpty()) {
            report.recordInvalid();
            return;
        }
        Optional<PictureRecord> existing = pictureRepository.findByContentSha256(
                request.businessArea(), metadata.get().contentSha256());
        if (existing.isPresent() && !record.getVoiceCode().equals(existing.get().getVoiceCode())) {
            report.recordConflicted();
            return;
        }
        if (!request.dryRun()) {
            maintenanceRepository.updateBackfillMetadata(
                    request.businessArea(),
                    record.getVoiceCode(),
                    metadata.get().contentSha256(),
                    metadata.get().fileSize(),
                    request.batchId(),
                    "LEGACY_BACKFILL",
                    request.operator(),
                    LocalDateTime.now());
        }
        report.recordBackfilled();
    }

    private Optional<Path> resolveExistingRecordPath(PictureRecord record) {
        if (record.getFilePath() == null || record.getFilePath().isBlank()) {
            return Optional.empty();
        }
        try {
            Path filePath = Path.of(record.getFilePath()).toAbsolutePath().normalize();
            return Files.isRegularFile(filePath) ? Optional.of(filePath) : Optional.empty();
        } catch (InvalidPathException ex) {
            return Optional.empty();
        }
    }
}
