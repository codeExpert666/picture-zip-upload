package com.example.picturezipupload.maintenance;

/**
 * 可写入日志和检查点的旧记录回填进度。
 */
public record PictureBackfillProgress(
        int processed,
        String lastVoiceCode,
        PictureMaintenanceReport report) {

    public PictureBackfillProgress {
        report = report.copy();
    }
}
