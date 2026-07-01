package com.example.picturezipupload.maintenance;

import com.example.picturezipupload.config.PictureUploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 显式启用后运行一次图片维护任务。
 */
@Component
@EnableConfigurationProperties(PictureMaintenanceProperties.class)
public class PictureMaintenanceRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PictureMaintenanceRunner.class);

    private final PictureMaintenanceProperties maintenanceProperties;
    private final PictureMaintenanceService maintenanceService;

    public PictureMaintenanceRunner(PictureMaintenanceProperties maintenanceProperties,
                                    PictureMaintenanceService maintenanceService) {
        this.maintenanceProperties = maintenanceProperties;
        this.maintenanceService = maintenanceService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!maintenanceProperties.isEnabled()) {
            return;
        }
        PictureMaintenanceMode mode = maintenanceProperties.getMode();
        if (mode == null) {
            throw new IllegalArgumentException("picture-maintenance.mode 不能为空");
        }
        PictureMaintenanceReport report = switch (mode) {
            case BACKFILL_EXISTING -> maintenanceService.backfillExistingRecords(
                    required("businessArea", maintenanceProperties.getBusinessArea()),
                    maintenanceProperties.getLegacyRoot(),
                    maintenanceProperties.getOperator(),
                    required("batchId", maintenanceProperties.getBatchId()),
                    maintenanceProperties.getLimit(),
                    maintenanceProperties.isDryRun());
            case IMPORT_DIRECT -> maintenanceService.importDirectDirectory(
                    required("businessArea", maintenanceProperties.getBusinessArea()),
                    required("sourceRoot", maintenanceProperties.getSourceRoot()),
                    required("publicUrlPrefix", maintenanceProperties.getPublicUrlPrefix()),
                    maintenanceProperties.getOperator(),
                    required("batchId", maintenanceProperties.getBatchId()),
                    maintenanceProperties.isDryRun());
        };
        log.info("图片维护任务完成: dryRun={}, mode={}, report={}",
                maintenanceProperties.isDryRun(), mode, report);
    }

    /**
     * 构造用于旧记录 {@code file_URL} 反解的静态资源映射。
     */
    public static StaticPicturePathResolver pathResolver(PictureUploadProperties uploadProperties) {
        Map<String, Path> mappings = new LinkedHashMap<>();
        mappings.put(uploadProperties.getPublicUrlPrefix(), uploadProperties.imagesPath());
        uploadProperties.getExtraStaticLocations().values().forEach(location -> {
            if (location.getPublicUrlPrefix() != null && location.getRootPath() != null) {
                mappings.put(location.getPublicUrlPrefix(), location.getRootPath());
            }
        });
        return new StaticPicturePathResolver(mappings);
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("picture-maintenance." + name + " 不能为空");
        }
        return value;
    }

    private static Path required(String name, Path value) {
        if (value == null) {
            throw new IllegalArgumentException("picture-maintenance." + name + " 不能为空");
        }
        return value;
    }
}
