package com.example.picturezipupload.maintenance;

import com.example.picturezipupload.config.PictureUploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 显式启用后运行一次图片维护任务。
 */
@Component
@EnableConfigurationProperties(PictureMaintenanceProperties.class)
public class PictureMaintenanceRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PictureMaintenanceRunner.class);

    private final PictureMaintenanceProperties maintenanceProperties;
    private final PictureUploadProperties uploadProperties;
    private final PictureMaintenanceService maintenanceService;
    private final PictureBackfillCheckpointStore checkpointStore;

    public PictureMaintenanceRunner(PictureMaintenanceProperties maintenanceProperties,
                                    PictureUploadProperties uploadProperties,
                                    PictureMaintenanceService maintenanceService,
                                    PictureBackfillCheckpointStore checkpointStore) {
        this.maintenanceProperties = maintenanceProperties;
        this.uploadProperties = uploadProperties;
        this.maintenanceService = maintenanceService;
        this.checkpointStore = checkpointStore;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!maintenanceProperties.isEnabled()) {
            return;
        }
        PictureMaintenanceMode mode = maintenanceProperties.getMode();
        if (mode == null) {
            throw new IllegalArgumentException("picture-maintenance.mode 不能为空");
        }
        PictureMaintenanceReport report = switch (mode) {
            case BACKFILL_EXISTING -> runBackfill();
            case IMPORT_DIRECT -> maintenanceService.importDirectDirectory(
                    required("businessArea", maintenanceProperties.getBusinessArea()),
                    defaultPath(maintenanceProperties.getSourceRoot(), uploadProperties.imagesPath()),
                    defaultText(maintenanceProperties.getPublicUrlPrefix(), uploadProperties.getPublicUrlPrefix()),
                    maintenanceProperties.getOperator(),
                    required("batchId", maintenanceProperties.getBatchId()),
                    maintenanceProperties.isDryRun());
        };
        log.info("图片维护任务完成: dryRun={}, mode={}, report={}",
                maintenanceProperties.isDryRun(), mode, report);
    }

    private PictureMaintenanceReport runBackfill() throws IOException {
        String businessArea = required("businessArea", maintenanceProperties.getBusinessArea());
        String batchId = required("batchId", maintenanceProperties.getBatchId());
        String operator = normalize(maintenanceProperties.getOperator());
        int limit = positive("limit", maintenanceProperties.getLimit());
        int batchSize = positive("batchSize", maintenanceProperties.getBatchSize());
        int progressInterval = positive("progressInterval", maintenanceProperties.getProgressInterval());
        String initialCursor = normalize(maintenanceProperties.getAfterVoiceCode());
        Path checkpointFile = checkpointFile(businessArea, batchId, maintenanceProperties.isDryRun());

        Optional<PictureBackfillCheckpoint> existingCheckpoint = checkpointStore.load(checkpointFile);
        PictureBackfillCheckpoint checkpoint = existingCheckpoint.orElseGet(() -> PictureBackfillCheckpoint.initial(
                businessArea,
                batchId,
                operator,
                maintenanceProperties.isDryRun(),
                limit,
                initialCursor));
        checkpoint.validate(
                businessArea,
                batchId,
                operator,
                maintenanceProperties.isDryRun(),
                limit,
                initialCursor);
        checkpointStore.save(checkpointFile, checkpoint);

        log.info("图片回填任务启动: dryRun={}, limit={}, batchSize={}, progressInterval={}, cursor={}, checkpoint={}",
                maintenanceProperties.isDryRun(), limit, batchSize, progressInterval,
                checkpoint.lastVoiceCode(), checkpointFile);
        if (checkpoint.exhausted()) {
            log.info("图片回填任务已由检查点标记为完成: checkpoint={}, report={}", checkpointFile, checkpoint.report());
            return checkpoint.report();
        }
        int remainingLimit = checkpoint.remainingLimit();
        if (remainingLimit == 0) {
            log.info("图片回填任务已达到 limit，未确认数据耗尽；如需继续，请使用新的 batch-id 并传入 "
                            + "--after-voice-code={}: checkpoint={}, report={}",
                    checkpoint.lastVoiceCode(), checkpointFile, checkpoint.report());
            return checkpoint.report();
        }

        PictureMaintenanceReport previousReport = checkpoint.report();
        PictureBackfillRequest request = new PictureBackfillRequest(
                businessArea,
                operator,
                batchId,
                batchSize,
                remainingLimit,
                progressInterval,
                checkpoint.lastVoiceCode(),
                maintenanceProperties.isDryRun());
        PictureBackfillResult result = maintenanceService.backfillExistingRecords(request, progress -> {
            PictureMaintenanceReport cumulativeReport = previousReport.plus(progress.report());
            PictureBackfillCheckpoint progressCheckpoint = checkpoint.advance(
                    progress.lastVoiceCode(), cumulativeReport, false);
            checkpointStore.save(checkpointFile, progressCheckpoint);
            log.info("图片回填进度: 本次已处理={}, cursor={}, 累计报告={}, checkpoint={}",
                    progress.processed(), progress.lastVoiceCode(), cumulativeReport, checkpointFile);
        });

        PictureMaintenanceReport cumulativeReport = previousReport.plus(result.report());
        PictureBackfillCheckpoint finalCheckpoint = checkpoint.advance(
                result.lastVoiceCode(), cumulativeReport, result.exhausted());
        checkpointStore.save(checkpointFile, finalCheckpoint);
        if (!result.exhausted()) {
            log.info("图片回填任务达到 limit，仍有候选记录；下一任务可从 --after-voice-code={} 继续",
                    result.lastVoiceCode());
        }
        return cumulativeReport;
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("picture-maintenance." + name + " 不能为空");
        }
        return value;
    }

    private static int positive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("picture-maintenance." + name + " 必须大于 0");
        }
        return value;
    }

    private static Path defaultPath(Path value, Path defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Path checkpointFile(String businessArea, String batchId, boolean dryRun) {
        Path configured = maintenanceProperties.getCheckpointFile();
        if (configured != null) {
            return configured.toAbsolutePath().normalize();
        }
        String suffix = dryRun ? "-dry-run" : "";
        String fileName = "backfill-" + fileSafe(businessArea) + "-" + fileSafe(batchId) + suffix + ".properties";
        return uploadProperties.getWorkRootPath()
                .resolve("maintenance")
                .resolve(fileName)
                .toAbsolutePath()
                .normalize();
    }

    private static String fileSafe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
