package com.example.picturezipupload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfiguration {

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
