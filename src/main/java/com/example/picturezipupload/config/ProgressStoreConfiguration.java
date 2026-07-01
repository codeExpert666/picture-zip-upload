package com.example.picturezipupload.config;

import com.example.picturezipupload.progress.InMemoryUploadProgressStore;
import com.example.picturezipupload.progress.RedisUploadProgressStore;
import com.example.picturezipupload.progress.UploadProgressStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ProgressStoreConfiguration {

    @Bean
    @ConditionalOnProperty(name = "picture-upload.progress-store", havingValue = "redis")
    public UploadProgressStore redisUploadProgressStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisUploadProgressStore(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(UploadProgressStore.class)
    public UploadProgressStore inMemoryUploadProgressStore() {
        return new InMemoryUploadProgressStore();
    }
}
