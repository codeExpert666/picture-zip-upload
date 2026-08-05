package com.example.picturezipupload.maintenance;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/**
 * 使用本地 properties 文件原子保存旧记录回填检查点。
 */
@Component
public class PictureBackfillCheckpointStore {

    private static final String VERSION = "1";

    public Optional<PictureBackfillCheckpoint> load(Path checkpointFile) throws IOException {
        Path normalizedFile = checkpointFile.toAbsolutePath().normalize();
        if (!Files.exists(normalizedFile)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(normalizedFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (!VERSION.equals(required(properties, "version"))) {
            throw new IOException("不支持的回填检查点版本: " + properties.getProperty("version"));
        }
        return Optional.of(new PictureBackfillCheckpoint(
                required(properties, "businessArea"),
                required(properties, "batchId"),
                optional(properties, "operator"),
                Boolean.parseBoolean(required(properties, "dryRun")),
                Integer.parseInt(required(properties, "limit")),
                optional(properties, "startAfterVoiceCode"),
                optional(properties, "lastVoiceCode"),
                Boolean.parseBoolean(required(properties, "exhausted")),
                Long.parseLong(required(properties, "scanned")),
                Long.parseLong(required(properties, "backfilled")),
                Long.parseLong(required(properties, "invalid")),
                Long.parseLong(required(properties, "missing")),
                Long.parseLong(required(properties, "conflicted")),
                required(properties, "updatedAt")));
    }

    public void save(Path checkpointFile, PictureBackfillCheckpoint checkpoint) throws IOException {
        Path normalizedFile = checkpointFile.toAbsolutePath().normalize();
        Path parent = normalizedFile.getParent();
        Files.createDirectories(parent);
        Properties properties = new Properties();
        properties.setProperty("version", VERSION);
        properties.setProperty("businessArea", checkpoint.businessArea());
        properties.setProperty("batchId", checkpoint.batchId());
        properties.setProperty("operator", text(checkpoint.operator()));
        properties.setProperty("dryRun", Boolean.toString(checkpoint.dryRun()));
        properties.setProperty("limit", Integer.toString(checkpoint.limit()));
        properties.setProperty("startAfterVoiceCode", text(checkpoint.startAfterVoiceCode()));
        properties.setProperty("lastVoiceCode", text(checkpoint.lastVoiceCode()));
        properties.setProperty("exhausted", Boolean.toString(checkpoint.exhausted()));
        properties.setProperty("scanned", Long.toString(checkpoint.scanned()));
        properties.setProperty("backfilled", Long.toString(checkpoint.backfilled()));
        properties.setProperty("invalid", Long.toString(checkpoint.invalid()));
        properties.setProperty("missing", Long.toString(checkpoint.missing()));
        properties.setProperty("conflicted", Long.toString(checkpoint.conflicted()));
        properties.setProperty("updatedAt", checkpoint.updatedAt());

        Path temporaryFile = Files.createTempFile(parent, normalizedFile.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                properties.store(writer, "picture backfill checkpoint");
            }
            moveAtomically(temporaryFile, normalizedFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IOException("回填检查点缺少字段: " + key);
        }
        return value;
    }

    private static String optional(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
