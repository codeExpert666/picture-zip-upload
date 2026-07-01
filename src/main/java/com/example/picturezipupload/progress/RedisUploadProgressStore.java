package com.example.picturezipupload.progress;

import com.example.picturezipupload.domain.UploadTaskProgress;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisUploadProgressStore implements UploadProgressStore {

    private static final String KEY_PREFIX = "picture-upload:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisUploadProgressStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(UploadTaskProgress progress) {
        try {
            redisTemplate.opsForValue().set(key(progress.getUploadId()), objectMapper.writeValueAsString(progress), TTL);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("上传进度序列化失败", ex);
        }
    }

    @Override
    public Optional<UploadTaskProgress> get(String uploadId) {
        String value = redisTemplate.opsForValue().get(key(uploadId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, UploadTaskProgress.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("上传进度反序列化失败", ex);
        }
    }

    private static String key(String uploadId) {
        return KEY_PREFIX + uploadId;
    }
}
