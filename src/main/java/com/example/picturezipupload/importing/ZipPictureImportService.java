package com.example.picturezipupload.importing;

import com.example.picturezipupload.config.PictureUploadProperties;
import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.domain.UploadTaskProgress;
import com.example.picturezipupload.progress.UploadProgressStore;
import com.example.picturezipupload.repository.PictureRecordRepository;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
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
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * zip 图片导入服务。
 *
 * <p>服务按 zip 条目流式处理图片，边写临时文件边计算 SHA-256，再根据内容哈希完成物理文件和数据库去重。</p>
 */
@Service
public class ZipPictureImportService {

    /**
     * 读取文件头用于图片魔数校验；当前支持格式的魔数最长不超过 12 字节。
     */
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

    /**
     * 异步导入合并后的 zip 文件。
     */
    @Async("pictureImportExecutor")
    public CompletableFuture<Void> importZipAsync(String uploadId, String originalZipName,
                                                  String businessArea, String operator, Path zipFile) {
        importZip(uploadId, originalZipName, businessArea, operator, zipFile);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 导入 zip 中的根目录图片文件。
     *
     * <p>单个非法条目只计入失败数，不中断整个压缩包；zip 读取或存储异常才会使任务失败。</p>
     */
    public void importZip(String uploadId, String originalZipName, String businessArea, String operator, Path zipFile) {
        UploadTaskProgress progress = progressStore.get(uploadId)
                .orElseGet(() -> UploadTaskProgress.processing(uploadId, originalZipName, businessArea, operator));
        if (progress.getBusinessArea() == null) {
            progress.setBusinessArea(businessArea);
        }
        if (progress.getOperator() == null) {
            progress.setOperator(operator);
        }
        progress.markProcessing();

        try {
            progress.setTotalFiles(countNonDirectoryEntries(zipFile));
            progressStore.save(progress);
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
                    importEntry(uploadId, originalZipName, businessArea, operator, zipInput, entry, progress);
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

    /**
     * 从 zip 中央目录统计需要进入导入循环的文件条目总数，供前端计算后台导入百分比。
     */
    private static long countNonDirectoryEntries(Path zipFile) throws IOException {
        long totalFiles = 0;
        try (ZipFile zip = ZipFile.builder()
                .setPath(zipFile)
                .setCharset(StandardCharsets.UTF_8)
                .setUseUnicodeExtraFields(true)
                .get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                if (!entries.nextElement().isDirectory()) {
                    totalFiles++;
                }
            }
        }
        return totalFiles;
    }

    /**
     * 处理单个 zip 条目。
     *
     * <p>重复图片只更新既有记录的导入元数据，不改变标注状态，也不覆盖原物理文件。</p>
     */
    private void importEntry(String uploadId, String originalZipName, String businessArea, String operator, InputStream input,
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
        Optional<PictureRecord> existing = repository.findByContentSha256(businessArea, candidate.sha256());
        if (existing.isPresent()) {
            Files.deleteIfExists(candidate.tempPath());
            repository.updateDuplicateImport(businessArea, candidate.sha256(), filename, normalizedExt,
                    uploadId, originalZipName, operator, LocalDateTime.now());
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
                operator,
                LocalDateTime.now());
        try {
            repository.insert(businessArea, record);
            progress.recordInserted();
        } catch (DuplicateKeyException ex) {
            // 数据库唯一索引是并发判重的最终防线。
            repository.updateDuplicateImport(businessArea, candidate.sha256(), filename, normalizedExt,
                    uploadId, originalZipName, operator, LocalDateTime.now());
            progress.recordDuplicated();
        }
    }

    /**
     * 将 zip 条目写入临时文件，同时计算内容 SHA-256 和读取文件头。
     */
    private StoredCandidate writeCandidate(String uploadId, InputStream input) throws IOException {
        Path importTempDir = properties.tempPath().resolve(uploadId);
        Files.createDirectories(importTempDir);
        Path tempPath = importTempDir.resolve(UUID.randomUUID() + ".part");
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[properties.getIoBufferSize()];
        ByteArrayOutputStream firstBytes = new ByteArrayOutputStream(FIRST_BYTES_LIMIT);
        long fileSize = 0;

        // 这里不能关闭 DigestInputStream，否则会连带关闭外层 ZipArchiveInputStream。
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

    /**
     * 将候选图片移动到内容哈希确定的正式路径。
     *
     * <p>如果并发任务已经创建了相同文件，当前临时文件直接删除，数据库唯一索引会继续兜底。</p>
     */
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

    /**
     * 图片 URL 与静态资源映射目录保持同样的两级哈希路径。
     */
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

    /**
     * 已写入临时目录、等待判重入库的图片候选文件。
     */
    private record StoredCandidate(Path tempPath, String sha256, long fileSize, byte[] firstBytes) {
    }
}
