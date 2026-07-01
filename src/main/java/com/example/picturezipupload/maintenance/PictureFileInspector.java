package com.example.picturezipupload.maintenance;

import com.example.picturezipupload.importing.ImageTypeDetector;
import com.example.picturezipupload.importing.PictureFileNameUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 读取本地图片文件并计算内容元数据。
 */
public class PictureFileInspector {

    private static final int FIRST_BYTES_LIMIT = 16;

    private final int ioBufferSize;

    public PictureFileInspector(int ioBufferSize) {
        this.ioBufferSize = ioBufferSize;
    }

    /**
     * 校验本地文件是否为支持的图片，并一次流式读取完成哈希和大小统计。
     *
     * <p>返回 {@link Optional#empty()} 表示该文件不应入库；调用方据此计入非法文件而不中断整批任务。</p>
     */
    public Optional<PictureFileMetadata> inspectImage(Path imagePath) throws IOException {
        if (!Files.isRegularFile(imagePath)) {
            return Optional.empty();
        }
        String baseName = PictureFileNameUtils.baseName(imagePath.getFileName().toString());
        String extname = PictureFileNameUtils.extractExtname(baseName);
        if (!ImageTypeDetector.isAllowedExtension(extname)) {
            return Optional.empty();
        }

        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[ioBufferSize];
        ByteArrayOutputStream firstBytes = new ByteArrayOutputStream(FIRST_BYTES_LIMIT);
        long fileSize = 0;
        // DigestInputStream 让哈希计算与文件读取共用同一轮 IO，避免对大图片重复读盘。
        try (InputStream input = new DigestInputStream(
                new BufferedInputStream(Files.newInputStream(imagePath)), digest)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (firstBytes.size() < FIRST_BYTES_LIMIT) {
                    int remaining = FIRST_BYTES_LIMIT - firstBytes.size();
                    firstBytes.write(buffer, 0, Math.min(read, remaining));
                }
                fileSize += read;
            }
        }

        String normalizedExt = ImageTypeDetector.normalizeExt(extname);
        if (!ImageTypeDetector.isSupportedImage(normalizedExt, firstBytes.toByteArray())) {
            return Optional.empty();
        }
        String filename = PictureFileNameUtils.truncate(
                PictureFileNameUtils.extractFilenameWithoutExt(baseName), 100);
        return Optional.of(new PictureFileMetadata(
                filename,
                normalizedExt,
                HexFormat.of().formatHex(digest.digest()),
                fileSize));
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }
}
