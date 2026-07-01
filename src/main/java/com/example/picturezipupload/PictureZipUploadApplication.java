package com.example.picturezipupload;

import com.example.picturezipupload.config.PictureUploadProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 图片压缩包上传样例应用入口。
 *
 * <p>启用异步任务用于后台导入压缩包，启用 MyBatis Mapper 扫描用于写入图片记录。</p>
 */
@EnableAsync
@SpringBootApplication
@MapperScan("com.example.picturezipupload.mapper")
@EnableConfigurationProperties(PictureUploadProperties.class)
public class PictureZipUploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(PictureZipUploadApplication.class, args);
    }
}
