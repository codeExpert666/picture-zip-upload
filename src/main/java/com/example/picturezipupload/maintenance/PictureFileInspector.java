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
        // 维护任务只处理真实文件；目录、符号异常路径等交给调用方按非法文件统计。
        if (!Files.isRegularFile(imagePath)) {
            return Optional.empty();
        }

        // 先用文件名做低成本过滤，避免明显不支持的文件继续触发磁盘读取和哈希计算。
        String baseName = PictureFileNameUtils.baseName(imagePath.getFileName().toString());
        String extname = PictureFileNameUtils.extractExtname(baseName);
        if (!ImageTypeDetector.isAllowedExtension(extname)) {
            return Optional.empty();
        }

        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[ioBufferSize];
        // 只保留文件头的一小段字节，用于后续魔数校验；图片内容本身不需要全部加载进内存。
        ByteArrayOutputStream firstBytes = new ByteArrayOutputStream(FIRST_BYTES_LIMIT);
        long fileSize = 0;
        // DigestInputStream 让哈希计算与文件读取共用同一轮 IO，避免对大图片重复读盘。
        try (InputStream input = new DigestInputStream(
                new BufferedInputStream(Files.newInputStream(imagePath)), digest)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                // 读取开始阶段顺手缓存文件头，缓存满后继续只做哈希和大小统计。
                if (firstBytes.size() < FIRST_BYTES_LIMIT) {
                    int remaining = FIRST_BYTES_LIMIT - firstBytes.size();
                    firstBytes.write(buffer, 0, Math.min(read, remaining));
                }
                fileSize += read;
            }
        }

        String normalizedExt = ImageTypeDetector.normalizeExt(extname);
        // 扩展名只是第一层筛选，最终仍以文件头特征判断是否为受支持的图片格式。
        if (!ImageTypeDetector.isSupportedImage(normalizedExt, firstBytes.toByteArray())) {
            return Optional.empty();
        }
        // 入库前统一去掉扩展名并限制长度，避免原始文件名过长影响数据库写入。
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
