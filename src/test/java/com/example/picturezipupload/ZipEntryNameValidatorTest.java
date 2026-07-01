package com.example.picturezipupload;

import com.example.picturezipupload.importing.ZipEntryNameValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZipEntryNameValidatorTest {

    @Test
    void acceptsSafeRelativeFilesIncludingNestedChineseDirectories() {
        assertThat(ZipEntryNameValidator.isSafeRelativeFile("a.jpg")).isTrue();
        assertThat(ZipEntryNameValidator.isSafeRelativeFile("中文目录/第一批/中文图片.png")).isTrue();
    }

    @Test
    void rejectsUnsafePaths() {
        assertThat(ZipEntryNameValidator.isSafeRelativeFile("dir\\a.jpg")).isFalse();
        assertThat(ZipEntryNameValidator.isSafeRelativeFile("../a.jpg")).isFalse();
        assertThat(ZipEntryNameValidator.isSafeRelativeFile("dir/../a.jpg")).isFalse();
        assertThat(ZipEntryNameValidator.isSafeRelativeFile("/a.jpg")).isFalse();
        assertThat(ZipEntryNameValidator.isSafeRelativeFile("")).isFalse();
    }
}
