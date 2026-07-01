package com.example.picturezipupload.maintenance;

import com.example.picturezipupload.config.PictureUploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片维护脚本依赖配置。
 */
@Configuration
@EnableConfigurationProperties(PictureMaintenanceProperties.class)
public class PictureMaintenanceConfiguration {

    /**
     * 复用上传模块的 IO buffer 配置，避免维护脚本另起一套读盘参数。
     */
    @Bean
    public PictureFileInspector pictureFileInspector(PictureUploadProperties properties) {
        return new PictureFileInspector(properties.getIoBufferSize());
    }

    /**
     * 同时纳入正式图片目录和额外静态目录，供旧记录 URL 反解使用。
     */
    @Bean
    public StaticPicturePathResolver staticPicturePathResolver(PictureUploadProperties properties) {
        return PictureMaintenanceRunner.pathResolver(properties);
    }
}
