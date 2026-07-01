package com.example.picturezipupload.importing;

/**
 * zip 条目名称校验工具。
 *
 * <p>当前业务只允许图片放在压缩包根目录，嵌套目录和路径穿越都应被过滤。</p>
 */
public final class ZipEntryNameValidator {

    private ZipEntryNameValidator() {
    }

    /**
     * 判断条目是否为根目录下的普通文件名。
     */
    public static boolean isRootFile(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return false;
        }
        String name = entryName.trim();
        return !name.startsWith("/")
                && !name.startsWith("\\")
                && !name.contains("/")
                && !name.contains("\\")
                && !name.equals(".")
                && !name.equals("..");
    }
}
