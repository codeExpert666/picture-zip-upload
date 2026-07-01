package com.example.picturezipupload.importing;

public final class ZipEntryNameValidator {

    private ZipEntryNameValidator() {
    }

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
