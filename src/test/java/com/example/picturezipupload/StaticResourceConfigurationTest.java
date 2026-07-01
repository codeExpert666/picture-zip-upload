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
    void registersDefaultAndExtraStaticResourceLocations() {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setRootPath(Path.of("/data/picture-upload"));
        properties.setPublicUrlPrefix("/api/pictures/files/");
        PictureUploadProperties.StaticLocation direct = new PictureUploadProperties.StaticLocation();
        direct.setRootPath(Path.of("/data/pictures"));
        direct.setPublicUrlPrefix("/api/pictures/direct/");
        properties.setExtraStaticLocations(Map.of("direct", direct));
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration defaultRegistration = mock(ResourceHandlerRegistration.class);
        ResourceHandlerRegistration directRegistration = mock(ResourceHandlerRegistration.class);
        when(registry.addResourceHandler("/api/pictures/files/**")).thenReturn(defaultRegistration);
        when(registry.addResourceHandler("/api/pictures/direct/**")).thenReturn(directRegistration);

        new StaticResourceConfiguration(properties).addResourceHandlers(registry);

        verify(defaultRegistration).addResourceLocations(Path.of("/data/picture-upload/images").toUri().toString());
        verify(directRegistration).addResourceLocations(Path.of("/data/pictures").toUri().toString());
    }
}
