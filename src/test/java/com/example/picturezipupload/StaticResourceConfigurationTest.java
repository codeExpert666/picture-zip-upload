package com.example.picturezipupload;

import com.example.picturezipupload.config.PictureUploadProperties;
import com.example.picturezipupload.web.StaticResourceConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.nio.file.Path;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaticResourceConfigurationTest {

    @Test
    void registersMainImageRootAndLegacyStaticResourceLocations() {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setWorkRootPath(Path.of("/data/picture-upload-work"));
        properties.setImageRootPath(Path.of("/data/pictures"));
        properties.setPublicUrlPrefix("/api/pictures/files/");
        PictureUploadProperties.StaticLocation legacy = new PictureUploadProperties.StaticLocation();
        legacy.setRootPath(Path.of("/data/corpusImages"));
        legacy.setPublicUrlPrefix("/corpusImages/");
        properties.setLegacyStaticLocations(Map.of("corpus-images", legacy));
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration defaultRegistration = mock(ResourceHandlerRegistration.class);
        ResourceHandlerRegistration legacyRegistration = mock(ResourceHandlerRegistration.class);
        when(registry.addResourceHandler("/api/pictures/files/**")).thenReturn(defaultRegistration);
        when(registry.addResourceHandler("/corpusImages/**")).thenReturn(legacyRegistration);

        new StaticResourceConfiguration(properties).addResourceHandlers(registry);

        verify(defaultRegistration).addResourceLocations(Path.of("/data/pictures").toUri().toString());
        verify(legacyRegistration).addResourceLocations(Path.of("/data/corpusImages").toUri().toString());
    }
}
