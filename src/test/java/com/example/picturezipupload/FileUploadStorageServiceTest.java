package com.example.picturezipupload;

import com.example.picturezipupload.config.PictureUploadProperties;
import com.example.picturezipupload.storage.FileUploadStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesChunksInIndexOrder() throws Exception {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setRootPath(tempDir);
        FileUploadStorageService service = new FileUploadStorageService(properties);

        service.saveChunk("upload-1", 1, new ByteArrayInputStream("world".getBytes()));
        service.saveChunk("upload-1", 0, new ByteArrayInputStream("hello ".getBytes()));

        Path merged = service.mergeChunks("upload-1", "images.zip", 2);

        assertThat(Files.readString(merged)).isEqualTo("hello world");
        assertThat(merged).exists();
    }

    @Test
    void listsUploadedChunkIndexesInAscendingOrder() throws Exception {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setRootPath(tempDir);
        FileUploadStorageService service = new FileUploadStorageService(properties);

        service.saveChunk("upload-1", 2, new ByteArrayInputStream("two".getBytes()));
        service.saveChunk("upload-1", 0, new ByteArrayInputStream("zero".getBytes()));
        Files.writeString(tempDir.resolve("chunks/upload-1/chunk-000001.part.tmp"), "partial");
        Files.writeString(tempDir.resolve("chunks/upload-1/readme.txt"), "ignore");

        assertThat(service.listUploadedChunkIndexes("upload-1")).containsExactly(0, 2);
    }

    @Test
    void deletesChunkDirectoryForCanceledUpload() throws Exception {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setRootPath(tempDir);
        FileUploadStorageService service = new FileUploadStorageService(properties);

        service.saveChunk("upload-1", 0, new ByteArrayInputStream("zero".getBytes()));
        Path chunkDir = tempDir.resolve("chunks/upload-1");
        Files.writeString(chunkDir.resolve("chunk-000001.part.tmp"), "partial");
        Files.writeString(chunkDir.resolve("readme.txt"), "ignore");

        service.deleteChunks("upload-1");

        assertThat(chunkDir).doesNotExist();
        assertThat(service.listUploadedChunkIndexes("upload-1")).isEmpty();
    }

    @Test
    void savesChunkWhenSha256ChecksumMatches() throws Exception {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setRootPath(tempDir);
        FileUploadStorageService service = new FileUploadStorageService(properties);

        service.saveChunk(
                "upload-1",
                0,
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
                "SHA-256",
                hexDigest("SHA-256", "hello"));

        Path merged = service.mergeChunks("upload-1", "images.zip", 1);
        assertThat(Files.readString(merged)).isEqualTo("hello");
    }

    @Test
    void rejectsMismatchedChecksumWithoutReplacingExistingChunk() throws Exception {
        PictureUploadProperties properties = new PictureUploadProperties();
        properties.setRootPath(tempDir);
        FileUploadStorageService service = new FileUploadStorageService(properties);
        service.saveChunk("upload-1", 0, new ByteArrayInputStream("original".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> service.saveChunk(
                "upload-1",
                0,
                new ByteArrayInputStream("broken".getBytes(StandardCharsets.UTF_8)),
                "MD5",
                "00000000000000000000000000000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片校验失败");

        assertThat(tempDir.resolve("chunks/upload-1/chunk-000000.part.tmp")).doesNotExist();
        Path merged = service.mergeChunks("upload-1", "images.zip", 1);
        assertThat(Files.readString(merged)).isEqualTo("original");
    }

    private static String hexDigest(String algorithm, String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
