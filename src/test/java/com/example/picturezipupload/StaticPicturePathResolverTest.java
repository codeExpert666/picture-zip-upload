package com.example.picturezipupload;

import com.example.picturezipupload.maintenance.StaticPicturePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticPicturePathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void encodesChinesePathSegmentsWithoutEncodingSlashes() {
        StaticPicturePathResolver resolver = new StaticPicturePathResolver();

        String fileUrl = resolver.fileUrlFor(tempDir.resolve("病理 图像").resolve("第一批").resolve("图片 001.png"),
                tempDir, "/api/pictures/files");

        assertThat(fileUrl).isEqualTo("/api/pictures/files/%E7%97%85%E7%90%86%20%E5%9B%BE%E5%83%8F/"
                + "%E7%AC%AC%E4%B8%80%E6%89%B9/%E5%9B%BE%E7%89%87%20001.png");
    }

    @Test
    void rejectsTraversalWhenCreatingFileUrl() {
        StaticPicturePathResolver resolver = new StaticPicturePathResolver();

        assertThatThrownBy(() -> resolver.fileUrlFor(tempDir.resolve("..").resolve("evil.png"),
                tempDir, "/api/pictures/files"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
