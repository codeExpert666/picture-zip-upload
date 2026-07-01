package com.example.picturezipupload;

import com.example.picturezipupload.controller.PictureZipUploadController;
import com.example.picturezipupload.domain.UploadStatus;
import com.example.picturezipupload.domain.UploadTaskProgress;
import com.example.picturezipupload.dto.CreateUploadRequest;
import com.example.picturezipupload.dto.CreateUploadResponse;
import com.example.picturezipupload.dto.UploadedChunksResponse;
import com.example.picturezipupload.dto.UploadProgressResponse;
import com.example.picturezipupload.service.PictureZipUploadService;
import com.example.picturezipupload.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PictureZipUploadControllerTest {

    private MockMvc mockMvc;

    private PictureZipUploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = mock(PictureZipUploadService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PictureZipUploadController(uploadService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void bindsBusinessAreaAndOperatorWhenCreatingUpload() throws Exception {
        when(uploadService.createUpload(any(CreateUploadRequest.class)))
                .thenReturn(new CreateUploadResponse("upload-1", UploadStatus.CREATED));

        mockMvc.perform(post("/api/picture-zip/uploads")
                        .contentType("application/json")
                        .content("""
                                {
                                  "originalFilename": "dataset.zip",
                                  "totalChunks": 2,
                                  "totalSize": 1024,
                                  "businessArea": "medical",
                                  "operator": "alice"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value("upload-1"))
                .andExpect(jsonPath("$.status").value("CREATED"));

        ArgumentCaptor<CreateUploadRequest> requestCaptor = forClass(CreateUploadRequest.class);
        verify(uploadService).createUpload(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getBusinessArea()).isEqualTo("medical");
        assertThat(requestCaptor.getValue().getOperator()).isEqualTo("alice");
    }

    @Test
    void returnsFieldValidationMessagesWhenCreateRequestInvalid() throws Exception {
        mockMvc.perform(post("/api/picture-zip/uploads")
                        .contentType("application/json")
                        .content("""
                                {
                                  "originalFilename": "",
                                  "totalChunks": 0,
                                  "totalSize": 0,
                                  "businessArea": "",
                                  "operator": "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("请求参数校验失败"))
                .andExpect(jsonPath("$.errors.originalFilename").value("原始文件名不能为空"))
                .andExpect(jsonPath("$.errors.totalChunks").value("总分片数必须大于等于 1"))
                .andExpect(jsonPath("$.errors.totalSize").value("文件总大小必须大于 0"))
                .andExpect(jsonPath("$.errors.businessArea").value("业务领域不能为空"))
                .andExpect(jsonPath("$.errors.operator").value("操作人长度不能超过 50 个字符"));
    }

    @Test
    void returnsUploadedChunksForResume() throws Exception {
        when(uploadService.uploadedChunks("upload-1"))
                .thenReturn(new UploadedChunksResponse(
                        "upload-1",
                        "dataset.zip",
                        UploadStatus.UPLOADING,
                        4,
                        2,
                        List.of(0, 2)));

        mockMvc.perform(get("/api/picture-zip/uploads/upload-1/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value("upload-1"))
                .andExpect(jsonPath("$.originalFilename").value("dataset.zip"))
                .andExpect(jsonPath("$.status").value("UPLOADING"))
                .andExpect(jsonPath("$.totalChunks").value(4))
                .andExpect(jsonPath("$.uploadedChunks").value(2))
                .andExpect(jsonPath("$.uploadedChunkIndexes[0]").value(0))
                .andExpect(jsonPath("$.uploadedChunkIndexes[1]").value(2));
    }

    @Test
    void forwardsChunkChecksumToService() throws Exception {
        UploadTaskProgress progress = UploadTaskProgress.created("upload-1", "dataset.zip", 2);
        progress.recordUploadedChunks(1);
        String checksum = "0000000000000000000000000000000000000000000000000000000000000000";
        when(uploadService.uploadChunk(
                eq("upload-1"),
                eq(0),
                any(InputStream.class),
                eq("SHA-256"),
                eq(checksum)))
                .thenReturn(UploadProgressResponse.from(progress));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "chunk.bin",
                "application/octet-stream",
                "chunk".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/picture-zip/uploads/upload-1/chunks/0")
                        .file(file)
                        .param("checksumAlgorithm", "SHA-256")
                        .param("checksum", checksum)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedChunks").value(1));

        verify(uploadService).uploadChunk(
                eq("upload-1"),
                eq(0),
                any(InputStream.class),
                eq("SHA-256"),
                eq(checksum));
    }

    @Test
    void cancelsUploadTask() throws Exception {
        mockMvc.perform(delete("/api/picture-zip/uploads/upload-1"))
                .andExpect(status().isNoContent());

        verify(uploadService).cancelUpload("upload-1");
    }

    @Test
    void exposesTotalFilesInProgressResponse() throws Exception {
        UploadTaskProgress progress = UploadTaskProgress.processing("upload-1", "dataset.zip");
        progress.setTotalFiles(10);
        progress.recordInserted();
        when(uploadService.progress("upload-1")).thenReturn(UploadProgressResponse.from(progress));

        mockMvc.perform(get("/api/picture-zip/uploads/upload-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiles").value(10))
                .andExpect(jsonPath("$.processedFiles").value(1));
    }
}
