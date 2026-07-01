package com.example.picturezipupload.maintenance;

/**
 * 维护脚本执行统计。
 *
 * <p>报告用于 dry-run 和正式执行后的人工核对，所有计数只表达处理结果，不承载分页状态。</p>
 */
public class PictureMaintenanceReport {

    private long scanned;
    private long inserted;
    private long duplicated;
    private long backfilled;
    private long invalid;
    private long missing;
    private long conflicted;

    public long getScanned() {
        return scanned;
    }

    public long getInserted() {
        return inserted;
    }

    public long getDuplicated() {
        return duplicated;
    }

    public long getBackfilled() {
        return backfilled;
    }

    public long getInvalid() {
        return invalid;
    }

    public long getMissing() {
        return missing;
    }

    public long getConflicted() {
        return conflicted;
    }

    void recordScanned() {
        scanned++;
    }

    void recordInserted() {
        inserted++;
    }

    void recordDuplicated() {
        duplicated++;
    }

    void recordBackfilled() {
        backfilled++;
    }

    void recordInvalid() {
        invalid++;
    }

    void recordMissing() {
        missing++;
    }

    void recordConflicted() {
        conflicted++;
    }

    @Override
    public String toString() {
        return "PictureMaintenanceReport{"
                + "scanned=" + scanned
                + ", inserted=" + inserted
                + ", duplicated=" + duplicated
                + ", backfilled=" + backfilled
                + ", invalid=" + invalid
                + ", missing=" + missing
                + ", conflicted=" + conflicted
                + '}';
    }
}
