package com.example.picturezipupload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 后台图片导入线程池配置。
 *
 * <p>压缩包合并后会异步解压和入库，线程池参数通过 {@link PictureUploadProperties} 控制。</p>
 */
@Configuration
public class AsyncConfiguration {

    /**
     * 图片导入专用线程池，避免 T 级压缩包处理占用 Web 请求线程。
     */
    @Bean("pictureImportExecutor")
    public Executor pictureImportExecutor(PictureUploadProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("picture-import-");
        executor.setCorePoolSize(properties.getImportCorePoolSize());
        executor.setMaxPoolSize(properties.getImportMaxPoolSize());
        executor.setQueueCapacity(properties.getImportQueueCapacity());
        executor.initialize();
        return executor;
    }
}
