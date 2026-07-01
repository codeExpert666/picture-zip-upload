package com.example.picturezipupload;

import com.example.picturezipupload.importing.ZipEntryNameValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZipEntryNameValidatorTest {

    @Test
    void acceptsOnlyFilesInZipRootDirectory() {
        assertThat(ZipEntryNameValidator.isRootFile("a.jpg")).isTrue();
        assertThat(ZipEntryNameValidator.isRootFile("中文图片.png")).isTrue();
    }

    @Test
    void rejectsNestedOrUnsafePaths() {
        assertThat(ZipEntryNameValidator.isRootFile("dir/a.jpg")).isFalse();
        assertThat(ZipEntryNameValidator.isRootFile("dir\\a.jpg")).isFalse();
        assertThat(ZipEntryNameValidator.isRootFile("../a.jpg")).isFalse();
        assertThat(ZipEntryNameValidator.isRootFile("/a.jpg")).isFalse();
        assertThat(ZipEntryNameValidator.isRootFile("")).isFalse();
    }
}
