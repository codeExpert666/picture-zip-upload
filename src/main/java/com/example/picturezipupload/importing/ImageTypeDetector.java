package com.example.picturezipupload.importing;

import java.util.Locale;
import java.util.Set;

/**
 * 图片类型检测工具。
 *
 * <p>先按扩展名做快速过滤，再用文件头魔数确认内容类型，避免把伪装扩展名的非图片入库。</p>
 */
public final class ImageTypeDetector {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "bmp", "gif");

    private ImageTypeDetector() {
    }

    /**
     * 判断扩展名是否在业务允许范围内。
     */
    public static boolean isAllowedExtension(String extname) {
        return SUPPORTED_EXTENSIONS.contains(normalizeExt(extname));
    }

    /**
     * 判断扩展名和文件头是否匹配受支持的图片格式。
     */
    public static boolean isSupportedImage(String extname, byte[] firstBytes) {
        String ext = normalizeExt(extname);
        if (!SUPPORTED_EXTENSIONS.contains(ext) || firstBytes == null) {
            return false;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> startsWith(firstBytes, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(firstBytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> startsWith(firstBytes, 'G', 'I', 'F', '8', '7', 'a')
                    || startsWith(firstBytes, 'G', 'I', 'F', '8', '9', 'a');
            case "webp" -> firstBytes.length >= 12
                    && startsWith(firstBytes, 'R', 'I', 'F', 'F')
                    && firstBytes[8] == 'W'
                    && firstBytes[9] == 'E'
                    && firstBytes[10] == 'B'
                    && firstBytes[11] == 'P';
            case "bmp" -> startsWith(firstBytes, 'B', 'M');
            default -> false;
        };
    }

    /**
     * 统一扩展名格式，数据库和文件路径都使用不带点的小写扩展名。
     */
    public static String normalizeExt(String extname) {
        if (extname == null) {
            return "";
        }
        String ext = extname.trim().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) {
            return ext.substring(1);
        }
        return ext;
    }

    private static boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((bytes[index] & 0xFF) != expected[index]) {
                return false;
            }
        }
        return true;
    }
}
