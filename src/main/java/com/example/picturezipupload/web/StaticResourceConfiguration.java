package com.example.picturezipupload.web;

import com.example.picturezipupload.config.PictureUploadProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 图片静态资源映射。
 *
 * <p>数据库中的 file_URL 与这里的映射前缀一致，前端可直接请求图片内容。</p>
 */
@Configuration
public class StaticResourceConfiguration implements WebMvcConfigurer {

    private final PictureUploadProperties properties;

    public StaticResourceConfiguration(PictureUploadProperties properties) {
        this.properties = properties;
    }

    /**
     * 将去重后的图片存储目录暴露为 HTTP 静态资源。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(properties.getPublicUrlPrefix() + "/**")
                .addResourceLocations(properties.imagesPath().toUri().toString());
    }
}
