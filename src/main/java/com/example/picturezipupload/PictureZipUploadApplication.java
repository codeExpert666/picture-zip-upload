package com.example.picturezipupload;

import com.example.picturezipupload.config.PictureUploadProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@MapperScan("com.example.picturezipupload.mapper")
@EnableConfigurationProperties(PictureUploadProperties.class)
public class PictureZipUploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(PictureZipUploadApplication.class, args);
    }
}
