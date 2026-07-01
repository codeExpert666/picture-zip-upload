package com.example.picturezipupload.web;

import com.example.picturezipupload.config.PictureUploadProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfiguration implements WebMvcConfigurer {

    private final PictureUploadProperties properties;

    public StaticResourceConfiguration(PictureUploadProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(properties.getPublicUrlPrefix() + "/**")
                .addResourceLocations(properties.imagesPath().toUri().toString());
    }
}
