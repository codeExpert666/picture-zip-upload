package com.example.picturezipupload.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 业务领域到图片表名的白名单解析器。
 *
 * <p>MyBatis 动态表名只能使用字符串替换，所以表名必须来自后端配置并通过 SQL 标识符校验。</p>
 */
@Component
public class BusinessAreaTableResolver {

    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Map<String, String> tableNamesByBusinessArea;

    @Autowired
    public BusinessAreaTableResolver(PictureUploadProperties properties) {
        this(properties.getBusinessAreaTables());
    }

    public BusinessAreaTableResolver(Map<String, String> tableNamesByBusinessArea) {
        this.tableNamesByBusinessArea = validateAndCopy(tableNamesByBusinessArea);
    }

    public String resolve(String businessArea) {
        String normalizedBusinessArea = normalizeBusinessArea(businessArea);
        String tableName = tableNamesByBusinessArea.get(normalizedBusinessArea);
        if (tableName == null) {
            throw new IllegalArgumentException("不支持的业务领域: " + normalizedBusinessArea);
        }
        return tableName;
    }

    private static Map<String, String> validateAndCopy(Map<String, String> tableNamesByBusinessArea) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (tableNamesByBusinessArea == null) {
            return copy;
        }
        tableNamesByBusinessArea.forEach((businessArea, tableName) -> {
            String normalizedBusinessArea = normalizeBusinessArea(businessArea);
            String normalizedTableName = normalizeTableName(tableName);
            copy.put(normalizedBusinessArea, normalizedTableName);
        });
        return Map.copyOf(copy);
    }

    private static String normalizeBusinessArea(String businessArea) {
        if (businessArea == null || businessArea.isBlank()) {
            throw new IllegalArgumentException("业务领域不能为空");
        }
        return businessArea.trim();
    }

    private static String normalizeTableName(String tableName) {
        if (tableName == null || tableName.isBlank()
                || !SAFE_SQL_IDENTIFIER.matcher(tableName.trim()).matches()) {
            throw new IllegalArgumentException("业务领域表名配置非法: " + tableName);
        }
        return tableName.trim();
    }
}
