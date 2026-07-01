package com.example.picturezipupload;

import com.example.picturezipupload.config.BusinessAreaTableResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessAreaTableResolverTest {

    @Test
    void resolvesConfiguredBusinessAreaToSafeTableName() {
        BusinessAreaTableResolver resolver = new BusinessAreaTableResolver(
                Map.of("medical", "medical_corpus_analysis_picture"));

        assertThat(resolver.resolve(" medical ")).isEqualTo("medical_corpus_analysis_picture");
    }

    @Test
    void rejectsUnknownBusinessArea() {
        BusinessAreaTableResolver resolver = new BusinessAreaTableResolver(
                Map.of("medical", "medical_corpus_analysis_picture"));

        assertThatThrownBy(() -> resolver.resolve("finance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的业务领域");
    }

    @Test
    void rejectsConfiguredTableNamesThatCannotBeUsedAsSqlIdentifiers() {
        assertThatThrownBy(() -> new BusinessAreaTableResolver(
                Map.of("medical", "medical_corpus_analysis_picture;drop table users")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("业务领域表名配置非法");
    }
}
