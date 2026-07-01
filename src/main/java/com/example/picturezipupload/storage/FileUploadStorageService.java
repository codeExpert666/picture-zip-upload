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

@Service
public class FileUploadStorageService {

    private final PictureUploadProperties properties;

    public FileUploadStorageService(PictureUploadProperties properties) {
        this.properties = properties;
    }

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

    private Path chunkPath(String uploadId, int chunkIndex) {
        return properties.chunksPath().resolve(uploadId).resolve(String.format("chunk-%06d.part", chunkIndex));
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
