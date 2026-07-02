package com.example.picturezipupload;

import com.example.picturezipupload.domain.PictureRecord;
import com.example.picturezipupload.domain.PictureStatus;
import com.example.picturezipupload.mapper.CorpusAnalysisPictureMapper;
import com.example.picturezipupload.mapper.param.UpdateBackfillMetadataParam;
import com.example.picturezipupload.mapper.param.UpdateDuplicateImportParam;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusAnalysisPictureMapperXmlTest {

    private static final String TABLE_NAME = "medical_corpus_analysis_picture";

    @Test
    void mapperMethodsDoNotExposeMoreThanFiveParameters() {
        List<String> violatingMethods = Arrays.stream(CorpusAnalysisPictureMapper.class.getDeclaredMethods())
                .filter(method -> method.getParameterCount() > 5)
                .map(Method::getName)
                .toList();

        assertThat(violatingMethods).isEmpty();
    }

    @Test
    void executesAllXmlMapperStatements() throws Exception {
        try (SqlSession session = sqlSessionFactory().openSession(true)) {
            createSchema(session.getConnection());
            CorpusAnalysisPictureMapper mapper = session.getMapper(CorpusAnalysisPictureMapper.class);
            LocalDateTime now = LocalDateTime.of(2026, 7, 1, 10, 0);
            PictureRecord record = record("voice-1", "first.png", "hash-1", 128L, now);

            mapper.insert(TABLE_NAME, record);

            PictureRecord inserted = mapper.findByContentSha256(TABLE_NAME, "hash-1").orElseThrow();
            assertThat(inserted.getVoiceCode()).isEqualTo("voice-1");
            assertThat(inserted.getFileUrl()).isEqualTo("/api/pictures/files/first.png");

            mapper.updateDuplicateImport(duplicateImportParam(now.plusMinutes(1)));

            PictureRecord duplicateUpdated = mapper.findByContentSha256(TABLE_NAME, "hash-1").orElseThrow();
            assertThat(duplicateUpdated.getFilename()).isEqualTo("updated.png");
            assertThat(duplicateUpdated.getUploadId()).isEqualTo("upload-2");
            assertThat(duplicateUpdated.getFilePath()).isEqualTo("/data/first.png");

            PictureRecord missingMetadata = record("voice-2", "missing.png", null, 0L, now.plusMinutes(2));
            mapper.insert(TABLE_NAME, missingMetadata);

            List<PictureRecord> recordsMissingMetadata = mapper.findRecordsMissingMetadata(TABLE_NAME, 10);
            assertThat(recordsMissingMetadata)
                    .extracting(PictureRecord::getVoiceCode)
                    .contains("voice-2");

            mapper.updateBackfillMetadata(backfillMetadataParam(now.plusMinutes(3)));

            PictureRecord backfilled = mapper.findByContentSha256(TABLE_NAME, "hash-2").orElseThrow();
            assertThat(backfilled.getFileSize()).isEqualTo(256L);
            assertThat(backfilled.getUploadId()).isEqualTo("legacy-upload");
            assertThat(backfilled.getOriginalZipName()).isEqualTo("legacy.zip");
        }
    }

    private static SqlSessionFactory sqlSessionFactory() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:picture_mapper_xml;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/CorpusAnalysisPictureMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments());
            mapperParser.parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS medical_corpus_analysis_picture");
            statement.execute("""
                    CREATE TABLE medical_corpus_analysis_picture (
                        voice_code VARCHAR(36) NOT NULL PRIMARY KEY,
                        filename VARCHAR(100),
                        extname VARCHAR(10),
                        file_URL VARCHAR(1024),
                        import_time TIMESTAMP,
                        update_time TIMESTAMP,
                        file_path VARCHAR(1024),
                        status VARCHAR(10),
                        content_sha256 CHAR(64),
                        file_size BIGINT,
                        upload_id VARCHAR(36),
                        original_zip_name VARCHAR(255),
                        operator VARCHAR(50)
                    )
                    """);
        }
    }

    private static UpdateDuplicateImportParam duplicateImportParam(LocalDateTime updateTime) {
        UpdateDuplicateImportParam param = new UpdateDuplicateImportParam();
        param.setTableName(TABLE_NAME);
        param.setContentSha256("hash-1");
        param.setFilename("updated.png");
        param.setExtname("png");
        param.setUploadId("upload-2");
        param.setOriginalZipName("updated.zip");
        param.setOperator("bob");
        param.setUpdateTime(updateTime);
        return param;
    }

    private static UpdateBackfillMetadataParam backfillMetadataParam(LocalDateTime updateTime) {
        UpdateBackfillMetadataParam param = new UpdateBackfillMetadataParam();
        param.setTableName(TABLE_NAME);
        param.setVoiceCode("voice-2");
        param.setContentSha256("hash-2");
        param.setFileSize(256L);
        param.setUploadId("legacy-upload");
        param.setOriginalZipName("legacy.zip");
        param.setOperator("carol");
        param.setUpdateTime(updateTime);
        return param;
    }

    private static PictureRecord record(String voiceCode, String filename, String contentSha256,
                                        long fileSize, LocalDateTime now) {
        PictureRecord record = new PictureRecord();
        record.setVoiceCode(voiceCode);
        record.setFilename(filename);
        record.setExtname("png");
        record.setFileUrl("/api/pictures/files/" + filename);
        record.setImportTime(now);
        record.setUpdateTime(now);
        record.setFilePath("/data/" + filename);
        record.setStatus(PictureStatus.MARK.name());
        record.setContentSha256(contentSha256);
        record.setFileSize(fileSize);
        record.setUploadId("upload-1");
        record.setOriginalZipName("original.zip");
        record.setOperator("alice");
        return record;
    }
}
