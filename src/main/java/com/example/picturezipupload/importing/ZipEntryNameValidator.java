package com.example.picturezipupload.importing;

/**
 * zip 条目名称校验工具。
 *
 * <p>允许 zip 内使用安全相对目录，绝对路径、路径穿越和 Windows 反斜杠路径都应被过滤。</p>
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

    /**
     * 判断条目是否为安全的相对文件路径。
     */
    public static boolean isSafeRelativeFile(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return false;
        }
        String name = entryName.trim();
        if (name.startsWith("/") || name.startsWith("\\") || name.contains("\\")) {
            return false;
        }
        String[] segments = name.split("/");
        if (segments.length == 0) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
