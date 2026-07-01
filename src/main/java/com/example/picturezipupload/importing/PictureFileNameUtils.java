package com.example.picturezipupload.importing;

/**
 * 图片文件名处理工具。
 */
public final class PictureFileNameUtils {

    private PictureFileNameUtils() {
    }

    /**
     * 从 zip 条目名或本地文件路径中提取最后一级文件名。
     */
    public static String baseName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0) {
            return normalized.substring(slashIndex + 1);
        }
        return normalized;
    }

    /**
     * 提取不带点的扩展名；无扩展名时返回空字符串。
     */
    public static String extractExtname(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }

    /**
     * 提取不带扩展名的文件名，供数据库 {@code filename} 字段保存展示名。
     */
    public static String extractFilenameWithoutExt(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0) {
            return filename;
        }
        return filename.substring(0, dotIndex);
    }

    /**
     * 按字符长度截断字符串，避免超过历史表字段长度。
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
