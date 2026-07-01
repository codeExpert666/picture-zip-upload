package com.example.picturezipupload.maintenance;

import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 静态图片路径和 URL 之间的转换工具。
 */
public class StaticPicturePathResolver {

    private final Map<String, Path> rootPathsByUrlPrefix;

    public StaticPicturePathResolver(Map<String, Path> rootPathsByUrlPrefix) {
        Map<String, Path> normalized = new LinkedHashMap<>();
        if (rootPathsByUrlPrefix != null) {
            rootPathsByUrlPrefix.forEach((prefix, rootPath) -> normalized.put(
                    normalizePrefix(prefix),
                    rootPath.toAbsolutePath().normalize()));
        }
        this.rootPathsByUrlPrefix = Map.copyOf(normalized);
    }

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
     * 根据已配置的静态资源映射，把数据库中的 {@code file_URL} 反解回本地文件路径。
     */
    public Optional<Path> resolveFileUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return Optional.empty();
        }
        String rawPath = rawPath(fileUrl.trim());
        for (Map.Entry<String, Path> mapping : rootPathsByUrlPrefix.entrySet()) {
            String prefix = mapping.getKey();
            if (rawPath.equals(prefix)) {
                return Optional.empty();
            }
            if (!rawPath.startsWith(prefix + "/")) {
                continue;
            }
            String relativeRawPath = rawPath.substring(prefix.length() + 1);
            Path relativePath = decodeRelativePath(relativeRawPath);
            validateRelativePath(relativePath);
            return Optional.of(mapping.getValue().resolve(relativePath).normalize());
        }
        return Optional.empty();
    }

    private static String rawPath(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() != null) {
                return uri.getRawPath();
            }
        } catch (IllegalArgumentException ignored) {
            // Relative URLs with unescaped Chinese characters still work through the fallback path below.
        }
        int queryIndex = value.indexOf('?');
        String path = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        int fragmentIndex = path.indexOf('#');
        return fragmentIndex >= 0 ? path.substring(0, fragmentIndex) : path;
    }

    private static Path decodeRelativePath(String relativeRawPath) {
        if (relativeRawPath.isBlank()) {
            throw new IllegalArgumentException("静态资源相对路径不能为空");
        }
        String[] segments = relativeRawPath.split("/");
        Path relativePath = Path.of(UriUtils.decode(segments[0], StandardCharsets.UTF_8));
        for (int index = 1; index < segments.length; index++) {
            relativePath = relativePath.resolve(UriUtils.decode(segments[index], StandardCharsets.UTF_8));
        }
        return relativePath;
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
