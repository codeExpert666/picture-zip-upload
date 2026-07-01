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
import java.util.List;
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
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("分片序号不能小于 0");
        }
        Path chunkDir = properties.chunksPath().resolve(uploadId);
        Files.createDirectories(chunkDir);

        Path chunkPath = chunkPath(uploadId, chunkIndex);
        Path tempPath = chunkPath.resolveSibling(chunkPath.getFileName() + ".tmp");
        try (InputStream input = new BufferedInputStream(inputStream)) {
            Files.copy(input, tempPath, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tempPath, chunkPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
}
