package com.example.picturezipupload.maintenance;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 将静态图片本地路径转换成 URL 的工具。
 */
public class StaticPicturePathResolver {

    /**
     * 将本地静态资源路径转换成可持久化到数据库的 URL。
     *
     * <p>相对路径中的中文、空格等字符按 path segment 编码，路径分隔符 {@code /} 保持不变。</p>
     */
    public String fileUrlFor(Path file, Path rootPath, String publicUrlPrefix) {
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("图片文件不在静态资源根目录下: " + file);
        }
        Path relativePath = normalizedRoot.relativize(normalizedFile);
        validateRelativePath(relativePath);
        // 只编码每一段路径，不能直接编码整个路径，否则斜杠会被转义导致 Spring 静态映射失效。
        String encodedPath = StreamSupport.stream(relativePath.spliterator(), false)
                .map(Path::toString)
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
        return normalizePrefix(publicUrlPrefix) + "/" + encodedPath;
    }

    /**
     * 防止维护脚本把静态根目录外的文件误映射进业务表。
     */
    private static void validateRelativePath(Path relativePath) {
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("静态资源相对路径不能是绝对路径: " + relativePath);
        }
        for (Path segment : relativePath) {
            String value = segment.toString();
            if (value.isBlank() || value.equals(".") || value.equals("..")
                    || value.contains("/") || value.contains("\\")) {
                throw new IllegalArgumentException("静态资源相对路径非法: " + relativePath);
            }
        }
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("静态资源 URL 前缀不能为空");
        }
        String normalized = prefix.trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
