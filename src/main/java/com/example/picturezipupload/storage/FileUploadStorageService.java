package com.example.picturezipupload.storage;

import com.example.picturezipupload.config.PictureUploadProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地文件系统上传存储服务。
 *
 * <p>负责分片落盘、顺序合并和临时分片清理，不参与图片判重和数据库写入。</p>
 */
@Service
public class FileUploadStorageService {

    private static final Pattern CHUNK_FILE_PATTERN = Pattern.compile("^chunk-(\\d{6})\\.part$");

    private final PictureUploadProperties properties;

    public FileUploadStorageService(PictureUploadProperties properties) {
        this.properties = properties;
    }

    /**
     * 保存一个分片。
     *
     * <p>先写临时文件再原子移动到正式分片名，避免进程中断时留下半个正式分片。</p>
     */
    public void saveChunk(String uploadId, int chunkIndex, InputStream inputStream) throws IOException {
        saveChunk(uploadId, chunkIndex, inputStream, null, null);
    }

    /**
     * 保存一个分片，并在移动到正式分片名前校验内容摘要。
     *
     * <p>校验失败时只删除本次临时文件，不覆盖同序号已上传成功的分片，便于前端重试。</p>
     */
    public void saveChunk(String uploadId, int chunkIndex, InputStream inputStream, String checksumAlgorithm,
                          String expectedChecksum) throws IOException {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("分片序号不能小于 0");
        }
        DigestSpec digestSpec = resolveDigestSpec(checksumAlgorithm, expectedChecksum);
        Path chunkDir = properties.chunksPath().resolve(uploadId);
        Files.createDirectories(chunkDir);

        Path chunkPath = chunkPath(uploadId, chunkIndex);
        Path tempPath = chunkPath.resolveSibling(chunkPath.getFileName() + ".tmp");
        try {
            copyChunk(inputStream, tempPath, digestSpec);
            Files.move(tempPath, chunkPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException ex) {
            deleteTempFile(tempPath, ex);
            throw ex;
        }
    }

    /**
     * 查询指定任务已完整落盘的分片序号。
     *
     * <p>只统计正式分片文件，忽略上传中断遗留的 .tmp 文件，供前端刷新后断点续传。</p>
     */
    public List<Integer> listUploadedChunkIndexes(String uploadId) throws IOException {
        Path chunkDir = properties.chunksPath().resolve(uploadId);
        if (!Files.isDirectory(chunkDir)) {
            return List.of();
        }
        try (var stream = Files.list(chunkDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .map(FileUploadStorageService::parseChunkIndex)
                    .flatMap(Optional::stream)
                    .sorted()
                    .toList();
        }
    }

    /**
     * 删除指定上传任务的全部分片文件。
     *
     * <p>取消上传时会连同已完成分片、上传中断留下的临时文件和无关旁路文件一起清掉。</p>
     */
    public void deleteChunks(String uploadId) throws IOException {
        deleteDirectoryIfExists(properties.chunksPath().resolve(uploadId));
    }

    /**
     * 按分片序号顺序合并 zip 文件。
     *
     * <p>任一分片缺失都会失败，避免生成不完整压缩包进入后台导入流程。</p>
     */
    public Path mergeChunks(String uploadId, String originalFilename, int totalChunks) throws IOException {
        if (totalChunks <= 0) {
            throw new IllegalArgumentException("分片总数必须大于 0");
        }
        Files.createDirectories(properties.zipsPath());
        Path mergedZip = properties.zipsPath().resolve(uploadId + ".zip");
        Files.deleteIfExists(mergedZip);

        byte[] buffer = new byte[properties.getIoBufferSize()];
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(
                mergedZip, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            for (int index = 0; index < totalChunks; index++) {
                Path chunk = chunkPath(uploadId, index);
                if (!Files.isRegularFile(chunk)) {
                    throw new IOException("缺少分片: " + index);
                }
                try (InputStream input = new BufferedInputStream(Files.newInputStream(chunk))) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
        deleteDirectoryIfExists(properties.chunksPath().resolve(uploadId));
        return mergedZip;
    }

    /**
     * 分片文件名使用固定宽度序号，便于排查目录内容时保持自然排序。
     */
    private Path chunkPath(String uploadId, int chunkIndex) {
        return properties.chunksPath().resolve(uploadId).resolve(String.format("chunk-%06d.part", chunkIndex));
    }

    private static Optional<Integer> parseChunkIndex(String filename) {
        Matcher matcher = CHUNK_FILE_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(matcher.group(1)));
    }

    private void copyChunk(InputStream inputStream, Path tempPath, DigestSpec digestSpec) throws IOException {
        MessageDigest digest = digestSpec == null ? null : newDigest(digestSpec.algorithm());
        byte[] buffer = new byte[properties.getIoBufferSize()];
        try (InputStream input = new BufferedInputStream(inputStream);
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(
                     tempPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE))) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (digest != null) {
                    digest.update(buffer, 0, read);
                }
                output.write(buffer, 0, read);
            }
        }

        if (digestSpec != null) {
            String actualChecksum = HexFormat.of().formatHex(digest.digest());
            if (!actualChecksum.equals(digestSpec.expectedChecksum())) {
                throw new IllegalArgumentException("分片校验失败: 期望 " + digestSpec.expectedChecksum()
                        + ", 实际 " + actualChecksum);
            }
        }
    }

    private static DigestSpec resolveDigestSpec(String checksumAlgorithm, String expectedChecksum) {
        boolean hasAlgorithm = hasText(checksumAlgorithm);
        boolean hasChecksum = hasText(expectedChecksum);
        if (!hasAlgorithm && !hasChecksum) {
            return null;
        }
        if (!hasAlgorithm || !hasChecksum) {
            throw new IllegalArgumentException("分片校验算法和校验值必须同时提供");
        }

        String algorithm = normalizeAlgorithm(checksumAlgorithm);
        String normalizedChecksum = expectedChecksum.trim().toLowerCase(Locale.ROOT);
        int expectedLength = switch (algorithm) {
            case "MD5" -> 32;
            case "SHA-256" -> 64;
            default -> throw new IllegalArgumentException("不支持的分片校验算法: " + checksumAlgorithm);
        };
        if (normalizedChecksum.length() != expectedLength || !normalizedChecksum.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException("分片校验值格式不正确");
        }
        return new DigestSpec(algorithm, normalizedChecksum);
    }

    private static String normalizeAlgorithm(String checksumAlgorithm) {
        String normalized = checksumAlgorithm.trim()
                .toUpperCase(Locale.ROOT)
                .replace("_", "-");
        if ("SHA256".equals(normalized)) {
            return "SHA-256";
        }
        return normalized;
    }

    private static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 不支持分片校验算法: " + algorithm, ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void deleteTempFile(Path tempPath, Exception cause) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException cleanupException) {
            cause.addSuppressed(cleanupException);
        }
    }

    private static void deleteDirectoryIfExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record DigestSpec(String algorithm, String expectedChecksum) {
    }
}
