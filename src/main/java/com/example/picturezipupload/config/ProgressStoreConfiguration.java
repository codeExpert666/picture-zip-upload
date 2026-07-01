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

/**
 * 上传进度存储配置。
 *
 * <p>通过配置项选择 Redis 或内存实现，业务代码只依赖 {@link UploadProgressStore}。</p>
 */
@Configuration
public class ProgressStoreConfiguration {

    /**
     * Redis 进度实现，适合多实例部署和进程重启后继续查询任务状态。
     */
    @Bean
    @ConditionalOnProperty(name = "picture-upload.progress-store", havingValue = "redis")
    public UploadProgressStore redisUploadProgressStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisUploadProgressStore(redisTemplate, objectMapper);
    }

    /**
     * 默认内存进度实现，便于本地样例运行，不适合生产多实例部署。
     */
    @Bean
    @ConditionalOnMissingBean(UploadProgressStore.class)
    public UploadProgressStore inMemoryUploadProgressStore() {
        return new InMemoryUploadProgressStore();
    }
}
