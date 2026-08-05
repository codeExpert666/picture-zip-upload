package com.example.picturezipupload.maintenance;

import java.time.Instant;
import java.util.Objects;

/**
 * 旧记录回填的持久化检查点。
 */
public record PictureBackfillCheckpoint(
        String businessArea,
        String batchId,
        String operator,
        boolean dryRun,
        int limit,
        String startAfterVoiceCode,
        String lastVoiceCode,
        boolean exhausted,
        long scanned,
        long backfilled,
        long invalid,
        long missing,
        long conflicted,
        String updatedAt) {

    public static PictureBackfillCheckpoint initial(String businessArea, String batchId, String operator,
                                                     boolean dryRun, int limit, String startAfterVoiceCode) {
        return new PictureBackfillCheckpoint(
                businessArea,
                batchId,
                normalize(operator),
                dryRun,
                limit,
                normalize(startAfterVoiceCode),
                normalize(startAfterVoiceCode),
                false,
                0,
                0,
                0,
                0,
                0,
                Instant.now().toString());
    }

    public PictureBackfillCheckpoint advance(String cursor, PictureMaintenanceReport report, boolean allRowsExhausted) {
        return new PictureBackfillCheckpoint(
                businessArea,
                batchId,
                operator,
                dryRun,
                limit,
                startAfterVoiceCode,
                normalize(cursor),
                allRowsExhausted,
                report.getScanned(),
                report.getBackfilled(),
                report.getInvalid(),
                report.getMissing(),
                report.getConflicted(),
                Instant.now().toString());
    }

    public PictureMaintenanceReport report() {
        return new PictureMaintenanceReport(scanned, 0, 0, backfilled, invalid, missing, conflicted);
    }

    public int remainingLimit() {
        long remaining = (long) limit - scanned;
        return remaining <= 0 ? 0 : Math.toIntExact(remaining);
    }

    public void validate(String expectedBusinessArea, String expectedBatchId, String expectedOperator,
                         boolean expectedDryRun, int expectedLimit, String expectedStartAfterVoiceCode) {
        if (!Objects.equals(businessArea, expectedBusinessArea)) {
            throw new IllegalStateException("检查点 business-area 与当前任务不一致");
        }
        if (!Objects.equals(batchId, expectedBatchId)) {
            throw new IllegalStateException("检查点 batch-id 与当前任务不一致");
        }
        if (!Objects.equals(operator, normalize(expectedOperator))) {
            throw new IllegalStateException("检查点 operator 与当前任务不一致");
        }
        if (dryRun != expectedDryRun) {
            throw new IllegalStateException("检查点 dry-run 与当前任务不一致");
        }
        if (limit != expectedLimit) {
            throw new IllegalStateException("检查点 limit 与当前任务不一致");
        }
        if (!Objects.equals(startAfterVoiceCode, normalize(expectedStartAfterVoiceCode))) {
            throw new IllegalStateException("检查点 after-voice-code 与当前任务不一致");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
