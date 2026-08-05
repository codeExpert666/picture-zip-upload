package com.example.picturezipupload.maintenance;

/**
 * 一次旧图片元数据回填执行的边界参数。
 */
public record PictureBackfillRequest(
        String businessArea,
        String operator,
        String batchId,
        int batchSize,
        int limit,
        int progressInterval,
        String afterVoiceCode,
        boolean dryRun) {

    public PictureBackfillRequest {
        if (businessArea == null || businessArea.isBlank()) {
            throw new IllegalArgumentException("businessArea 不能为空");
        }
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("batchId 不能为空");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 必须大于 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }
        if (progressInterval <= 0) {
            throw new IllegalArgumentException("progressInterval 必须大于 0");
        }
        afterVoiceCode = normalize(afterVoiceCode);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
