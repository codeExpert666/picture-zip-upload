package com.example.picturezipupload.controller;

import com.example.picturezipupload.dto.CreateUploadRequest;
import com.example.picturezipupload.dto.CreateUploadResponse;
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

@RestController
@RequestMapping("/api/picture-zip/uploads")
public class PictureZipUploadController {

    private final PictureZipUploadService uploadService;

    public PictureZipUploadController(PictureZipUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    public CreateUploadResponse create(@Valid @RequestBody CreateUploadRequest request) {
        return uploadService.createUpload(request);
    }

    @PutMapping("/{uploadId}/chunks/{chunkIndex}")
    public UploadProgressResponse uploadChunk(@PathVariable String uploadId,
                                              @PathVariable int chunkIndex,
                                              @RequestParam("file") MultipartFile file) throws IOException {
        return uploadService.uploadChunk(uploadId, chunkIndex, file.getInputStream());
    }

    @PostMapping("/{uploadId}/complete")
    public UploadProgressResponse complete(@PathVariable String uploadId) throws IOException {
        return uploadService.complete(uploadId);
    }

    @GetMapping("/{uploadId}")
    public UploadProgressResponse progress(@PathVariable String uploadId) {
        return uploadService.progress(uploadId);
    }
}
