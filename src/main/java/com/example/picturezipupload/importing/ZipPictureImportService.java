package com.example.picturezipupload.importing;

import com.example.picturezipupload.config.PictureUploadProperties;
import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.domain.UploadTaskProgress;
import com.example.picturezipupload.progress.UploadProgressStore;
import com.example.picturezipupload.repository.PictureRecordRepository;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class ZipPictureImportService {

    private static final int FIRST_BYTES_LIMIT = 16;

    private final PictureUploadProperties properties;
    private final PictureRecordRepository repository;
    private final UploadProgressStore progressStore;

    public ZipPictureImportService(PictureUploadProperties properties, PictureRecordRepository repository,
                                   UploadProgressStore progressStore) {
        this.properties = properties;
        this.repository = repository;
        this.progressStore = progressStore;
    }

    @Async("pictureImportExecutor")
    public CompletableFuture<Void> importZipAsync(String uploadId, String originalZipName, Path zipFile) {
        importZip(uploadId, originalZipName, zipFile);
        return CompletableFuture.completedFuture(null);
    }

    public void importZip(String uploadId, String originalZipName, Path zipFile) {
        UploadTaskProgress progress = progressStore.get(uploadId)
                .orElseGet(() -> UploadTaskProgress.processing(uploadId, originalZipName));
        progress.markProcessing();
        progressStore.save(progress);

        try {
            Files.createDirectories(properties.imagesPath());
            Files.createDirectories(properties.tempPath());
            try (InputStream fileInput = new BufferedInputStream(Files.newInputStream(zipFile));
                 ZipArchiveInputStream zipInput = new ZipArchiveInputStream(
                         fileInput, StandardCharsets.UTF_8.name(), true, true)) {
                ZipArchiveEntry entry;
                while ((entry = zipInput.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    importEntry(uploadId, originalZipName, zipInput, entry, progress);
                    progressStore.save(progress);
                }
            }
            progress.markDone();
            progressStore.save(progress);
        } catch (Exception ex) {
            progress.markFailed(ex.getMessage());
            progressStore.save(progress);
            throw new IllegalStateException("压缩包导入失败: " + zipFile, ex);
        }
    }

    private void importEntry(String uploadId, String originalZipName, InputStream input,
                             ZipArchiveEntry entry, UploadTaskProgress progress) throws IOException {
        String entryName = entry.getName();
        if (!ZipEntryNameValidator.isRootFile(entryName)) {
            progress.recordFailedFile();
            return;
        }
        String extname = extractExtname(entryName);
        if (!ImageTypeDetector.isAllowedExtension(extname)) {
            progress.recordFailedFile();
            return;
        }

        StoredCandidate candidate = writeCandidate(uploadId, input);
        if (!ImageTypeDetector.isSupportedImage(extname, candidate.firstBytes())) {
            Files.deleteIfExists(candidate.tempPath());
            progress.recordFailedFile();
            return;
        }

        String normalizedExt = ImageTypeDetector.normalizeExt(extname);
        String filename = truncate(extractFilenameWithoutExt(entryName), 100);
        Optional<PictureRecord> existing = repository.findByContentSha256(candidate.sha256());
        if (existing.isPresent()) {
            Files.deleteIfExists(candidate.tempPath());
            repository.updateDuplicateImport(candidate.sha256(), filename, normalizedExt,
                    uploadId, originalZipName, LocalDateTime.now());
            progress.recordDuplicated();
            return;
        }

        Path finalPath = finalImagePath(candidate.sha256(), normalizedExt);
        moveCandidateToFinalPath(candidate.tempPath(), finalPath);
        PictureRecord record = PictureRecord.imported(
                filename,
                normalizedExt,
                publicUrlFor(candidate.sha256(), normalizedExt),
                finalPath.toAbsolutePath().toString(),
                candidate.sha256(),
                candidate.fileSize(),
                uploadId,
                originalZipName,
                LocalDateTime.now());
        try {
            repository.insert(record);
            progress.recordInserted();
        } catch (DuplicateKeyException ex) {
            // 数据库唯一索引是并发判重的最终防线。
            repository.updateDuplicateImport(candidate.sha256(), filename, normalizedExt,
                    uploadId, originalZipName, LocalDateTime.now());
            progress.recordDuplicated();
        }
    }

    private StoredCandidate writeCandidate(String uploadId, InputStream input) throws IOException {
        Path importTempDir = properties.tempPath().resolve(uploadId);
        Files.createDirectories(importTempDir);
        Path tempPath = importTempDir.resolve(UUID.randomUUID() + ".part");
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[properties.getIoBufferSize()];
        ByteArrayOutputStream firstBytes = new ByteArrayOutputStream(FIRST_BYTES_LIMIT);
        long fileSize = 0;

        DigestInputStream digestInput = new DigestInputStream(input, digest);
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(
                     tempPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            int read;
            while ((read = digestInput.read(buffer)) != -1) {
                if (firstBytes.size() < FIRST_BYTES_LIMIT) {
                    int remaining = FIRST_BYTES_LIMIT - firstBytes.size();
                    firstBytes.write(buffer, 0, Math.min(read, remaining));
                }
                output.write(buffer, 0, read);
                fileSize += read;
            }
        }
        return new StoredCandidate(tempPath, HexFormat.of().formatHex(digest.digest()), fileSize, firstBytes.toByteArray());
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }

    private Path finalImagePath(String sha256, String extname) {
        return properties.imagesPath().resolve(sha256.substring(0, 2)).resolve(sha256 + "." + extname);
    }

    private void moveCandidateToFinalPath(Path tempPath, Path finalPath) throws IOException {
        Files.createDirectories(finalPath.getParent());
        try {
            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException ex) {
            Files.deleteIfExists(tempPath);
        } catch (IOException ex) {
            if (Files.exists(finalPath)) {
                Files.deleteIfExists(tempPath);
                return;
            }
            Files.move(tempPath, finalPath);
        }
    }

    private String publicUrlFor(String sha256, String extname) {
        return properties.getPublicUrlPrefix() + "/" + sha256.substring(0, 2) + "/" + sha256 + "." + extname;
    }

    private static String extractExtname(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }

    private static String extractFilenameWithoutExt(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0) {
            return filename;
        }
        return filename.substring(0, dotIndex);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record StoredCandidate(Path tempPath, String sha256, long fileSize, byte[] firstBytes) {
    }
}
