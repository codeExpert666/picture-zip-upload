package com.example.picturezipupload.maintenance;

/**
 * 从本地图片文件读取出的导入元数据。
 *
 * @param filename 图片展示名，不含扩展名
 * @param extname 规范化后的小写扩展名
 * @param contentSha256 图片内容 SHA-256 十六进制摘要
 * @param fileSize 图片文件字节数
 */
public record PictureFileMetadata(String filename, String extname, String contentSha256, long fileSize) {
}
