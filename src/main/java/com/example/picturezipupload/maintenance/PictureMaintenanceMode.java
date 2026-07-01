package com.example.picturezipupload.maintenance;

/**
 * 图片维护脚本运行模式。
 */
public enum PictureMaintenanceMode {
    /**
     * 回填已经有数据库记录的历史图片新增字段。
     */
    BACKFILL_EXISTING,

    /**
     * 将数据组直接上传到服务器目录的图片按原地引用方式入库。
     */
    IMPORT_DIRECT
}
