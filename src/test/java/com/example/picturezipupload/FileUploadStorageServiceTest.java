package com.example.picturezipupload;

import com.example.picturezipupload.config.PictureUploadProperties;
import com.example.picturezipupload.storage.FileUploadStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
}
