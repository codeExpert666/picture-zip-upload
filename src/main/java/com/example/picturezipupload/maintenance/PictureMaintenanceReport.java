package com.example.picturezipupload.maintenance;

/**
 * 维护脚本执行统计。
 *
 * <p>报告用于 dry-run 和正式执行后的人工核对，所有计数只表达处理结果，不承载分页状态。</p>
 */
public class PictureMaintenanceReport {

    /**
     * 当前任务扫描或读取到的文件/记录数量。
     */
    private long scanned;

    /**
     * 新目录导入中可插入或已插入的记录数量；dry-run 时表示预计写入量。
     */
    private long inserted;

    /**
     * 内容哈希已存在而跳过的图片数量。
     */
    private long duplicated;

    /**
     * 旧记录回填中可回填或已回填的记录数量；dry-run 时表示预计回填量。
     */
    private long backfilled;

    /**
     * 扩展名不支持、魔数不匹配或文件内容不是有效图片的数量。
     */
    private long invalid;

    /**
     * 旧记录无法定位到本地文件的数量。
     */
    private long missing;

    /**
     * 旧记录重新计算出的内容哈希与其他记录冲突的数量。
     */
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
