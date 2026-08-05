package com.example.picturezipupload.maintenance;

/**
 * 一次旧记录回填执行结果。
 */
public record PictureBackfillResult(
        PictureMaintenanceReport report,
        String lastVoiceCode,
        boolean exhausted) {
}
