package com.example.picturezipupload.controller;

import com.example.picturezipupload.dto.CreateUploadRequest;
import com.example.picturezipupload.dto.CreateUploadResponse;
import com.example.picturezipupload.dto.UploadedChunksResponse;
import com.example.picturezipupload.dto.UploadProgressResponse;
import com.example.picturezipupload.service.PictureZipUploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片压缩包上传接口。
 *
 * <p>接口采用“创建任务 -> 上传分片 -> 完成合并 -> 查询进度”的流程，避免 T 级文件占用单个请求。</p>
 */
@RestController
@RequestMapping("/api/picture-zip/uploads")
public class PictureZipUploadController {

    private final PictureZipUploadService uploadService;

    public PictureZipUploadController(PictureZipUploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * 创建一次压缩包上传任务，返回后续分片上传使用的 uploadId。
     */
    @PostMapping
    public CreateUploadResponse create(@Valid @RequestBody CreateUploadRequest request) {
        return uploadService.createUpload(request);
    }

    /**
     * 上传单个分片；分片序号从 0 开始，服务端完成时会按序号合并。
     */
    @PutMapping("/{uploadId}/chunks/{chunkIndex}")
    public UploadProgressResponse uploadChunk(@PathVariable String uploadId,
                                              @PathVariable int chunkIndex,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "checksumAlgorithm", required = false)
                                              String checksumAlgorithm,
                                              @RequestParam(value = "checksum", required = false)
                                              String checksum) throws IOException {
        return uploadService.uploadChunk(uploadId, chunkIndex, file.getInputStream(), checksumAlgorithm, checksum);
    }

    /**
     * 查询已上传分片列表，用于前端断点续传。
     */
    @GetMapping("/{uploadId}/chunks")
    public UploadedChunksResponse uploadedChunks(@PathVariable String uploadId) throws IOException {
        return uploadService.uploadedChunks(uploadId);
    }

    /**
     * 标记分片上传完成，触发服务端合并 zip 并提交后台导入任务。
     */
    @PostMapping("/{uploadId}/complete")
    public UploadProgressResponse complete(@PathVariable String uploadId) throws IOException {
        return uploadService.complete(uploadId);
    }

    /**
     * 查询上传和后台导入进度。
     */
    @GetMapping("/{uploadId}")
    public UploadProgressResponse progress(@PathVariable String uploadId) {
        return uploadService.progress(uploadId);
    }
}
