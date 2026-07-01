package com.example.picturezipupload;

import com.example.picturezipupload.maintenance.StaticPicturePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticPicturePathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void encodesChinesePathSegmentsWithoutEncodingSlashes() {
        StaticPicturePathResolver resolver = new StaticPicturePathResolver(
                Map.of("/api/pictures/direct", tempDir));

        String fileUrl = resolver.fileUrlFor(tempDir.resolve("病理 图像").resolve("第一批").resolve("图片 001.png"),
                tempDir, "/api/pictures/direct");

        assertThat(fileUrl).isEqualTo("/api/pictures/direct/%E7%97%85%E7%90%86%20%E5%9B%BE%E5%83%8F/"
                + "%E7%AC%AC%E4%B8%80%E6%89%B9/%E5%9B%BE%E7%89%87%20001.png");
    }

    @Test
    void decodesConfiguredFileUrlBackToLocalPath() {
        StaticPicturePathResolver resolver = new StaticPicturePathResolver(
                Map.of("/api/pictures/direct", tempDir));

        Path path = resolver.resolveFileUrl("/api/pictures/direct/%E7%97%85%E7%90%86/%E5%9B%BE%E7%89%87.png")
                .orElseThrow();

        assertThat(path).isEqualTo(tempDir.resolve("病理").resolve("图片.png").normalize());
    }

    @Test
    void rejectsTraversalWhenCreatingFileUrl() {
        StaticPicturePathResolver resolver = new StaticPicturePathResolver(
                Map.of("/api/pictures/direct", tempDir));

        assertThatThrownBy(() -> resolver.fileUrlFor(tempDir.resolve("..").resolve("evil.png"),
                tempDir, "/api/pictures/direct"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
