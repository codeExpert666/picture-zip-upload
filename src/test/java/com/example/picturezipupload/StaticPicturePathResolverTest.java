package com.example.picturezipupload;

import com.example.picturezipupload.config.PictureUploadProperties;
import com.example.picturezipupload.maintenance.PictureMaintenanceRunner;
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
                Map.of("/api/pictures/files", tempDir));

        String fileUrl = resolver.fileUrlFor(tempDir.resolve("病理 图像").resolve("第一批").resolve("图片 001.png"),
                tempDir, "/api/pictures/files");

        assertThat(fileUrl).isEqualTo("/api/pictures/files/%E7%97%85%E7%90%86%20%E5%9B%BE%E5%83%8F/"
                + "%E7%AC%AC%E4%B8%80%E6%89%B9/%E5%9B%BE%E7%89%87%20001.png");
    }

    @Test
    void decodesConfiguredFileUrlBackToLocalPath() {
        StaticPicturePathResolver resolver = new StaticPicturePathResolver(
                Map.of("/api/pictures/files", tempDir));

        Path path = resolver.resolveFileUrl("/api/pictures/files/%E7%97%85%E7%90%86/%E5%9B%BE%E7%89%87.png")
                .orElseThrow();

        assertThat(path).isEqualTo(tempDir.resolve("病理").resolve("图片.png").normalize());
    }

    @Test
    void maintenancePathResolverIncludesMainAndLegacyStaticMappings() {
        Path imageRoot = tempDir.resolve("pictures");
        Path legacyRoot = tempDir.resolve("corpusImages");
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setImageRootPath(imageRoot);
        properties.setPublicUrlPrefix("/api/pictures/files");
        PictureUploadProperties.StaticLocation legacy = new PictureUploadProperties.StaticLocation();
        legacy.setRootPath(legacyRoot);
        legacy.setPublicUrlPrefix("/corpusImages");
        properties.setLegacyStaticLocations(Map.of("corpus-images", legacy));

        StaticPicturePathResolver resolver = PictureMaintenanceRunner.pathResolver(properties);

        assertThat(resolver.resolveFileUrl("/api/pictures/files/ab/new.png"))
                .contains(imageRoot.resolve("ab").resolve("new.png").normalize());
        assertThat(resolver.resolveFileUrl("/corpusImages/old.png"))
                .contains(legacyRoot.resolve("old.png").normalize());
    }

    @Test
    void rejectsTraversalWhenCreatingFileUrl() {
        StaticPicturePathResolver resolver = new StaticPicturePathResolver(
                Map.of("/api/pictures/files", tempDir));

        assertThatThrownBy(() -> resolver.fileUrlFor(tempDir.resolve("..").resolve("evil.png"),
                tempDir, "/api/pictures/files"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
