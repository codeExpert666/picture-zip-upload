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
     * 为新目录导入生成分段编码后的静态资源 URL。
     */
    @Bean
    public StaticPicturePathResolver staticPicturePathResolver() {
        return new StaticPicturePathResolver();
    }
}
