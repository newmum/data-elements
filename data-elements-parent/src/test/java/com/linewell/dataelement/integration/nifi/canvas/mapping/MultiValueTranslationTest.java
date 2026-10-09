package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class MultiValueTranslationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void standardPreviewPreservesOrderDuplicatesUnknownsAndLiteralSeparators() throws Exception {
        var lookup = Map.of("multiValue", true, "values", Map.of("U", "未知", "F", "女'性"),
                "multiValueSeparator", "||");
        var spec = Map.of("version", "1.0", "mappings", List.of(Map.of("from", "/code", "to", "/label", "lookup", lookup)));
        var service = new FieldMappingService();
        var preview = service.preview(spec, List.of(Map.of("code", " U || X ||F|| U || "), Map.of("code", "X||Y")));
        assertThat(preview.resultRows().getFirst().get("label")).isEqualTo("未知||X||女'性||未知");
        assertThat(preview.resultRows().get(1).get("label")).isNull();
        assertThat(service.supportsSourceDbPushdown(spec)).isFalse();
        assertThat(service.compilePlan(spec).lookups().getFirst().dataSource()).isEmpty();
        for (String separator : List.of("\"", "\\", ".", "|")) {
            var rule = MultiValueTranslation.rule(JSON.valueToTree(Map.of("multiValue", true,
                    "values", Map.of("U", "未知", "F", "女"), "multiValueSeparator", separator)));
            assertThat(MultiValueTranslation.translate("U" + separator + "F", rule, rule.values()))
                    .isEqualTo("未知" + separator + "女");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Oracle", "PostgreSQL", "MySQL", "MSSQLServer", "DB2", "REGULAR"})
    void ordinaryParameterizedQueryWorksWithoutDatabaseSpecificFunctions(String mode) throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=" + mode;
        try (var connection = DriverManager.getConnection(url)) {
            connection.createStatement().execute("create table dictionary(code varchar(32), label varchar(100), enabled int)");
            connection.createStatement().execute("insert into dictionary values('U','未知',1),('F','女',1),('X','隐藏',0)");
            var rule = MultiValueTranslation.rule(JSON.valueToTree(Map.of("multiValue", true,
                    "query", "SELECT code, label FROM dictionary WHERE enabled = 1 AND code IN (:codes)")));
            var values = MultiValueTranslation.dictionary(connection, rule, List.of("U", "F", "X", "x' OR 1=1 --"));
            assertThat(MultiValueTranslation.translate(" U ,X,F,U ", rule, values)).isEqualTo("未知,X,女,未知");
            assertThat(MultiValueTranslation.translate("X,Y", rule, values)).isNull();
            assertThat(values).hasSize(2);
        }
    }

    @Test
    void numericDictionaryRejectsNoDatabaseTypeAndTreatsInvalidCodesAsUnknown() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID())) {
            connection.createStatement().execute("create table dictionary(code int, label varchar(100))");
            connection.createStatement().execute("insert into dictionary values(1,'一'),(2,'二')");
            var rule = MultiValueTranslation.rule(JSON.valueToTree(Map.of("multiValue", true,
                    "query", "SELECT code, label FROM dictionary WHERE code IN (:codes)")));
            var values = MultiValueTranslation.dictionary(connection, rule, List.of("1", "2", "bad", "01", "1.00"));
            assertThat(MultiValueTranslation.translate("1,bad,2,01,1.00", rule, values)).isEqualTo("一,bad,二,一,一");
        }
    }

    @Test
    void legacyGeneratedDictionaryKeepsSchemaAndFiltersDuringRepair() {
        String sql = "SELECT CASE FROM JSON_TABLE(...) tokens LEFT JOIN "
                + "(SELECT code AS dict_code, MAX(label) AS dict_name FROM PUBLIC.DICT WHERE enabled = 1 AND category = 'gender' GROUP BY code) dict ON 1=1";
        assertThat(MultiValueTranslation.legacyDictionaryQuery(sql, "DICT", "code", "label"))
                .isEqualTo("SELECT code, label FROM PUBLIC.DICT WHERE enabled = 1 AND category = 'gender' AND code IN (:codes)");
        assertThat(MultiValueTranslation.legacyDictionaryQuery(sql.replace("enabled = 1", "enabled IN (1, 2)"), "DICT", "code", "label"))
                .contains("enabled IN (1, 2)", "category = 'gender'", "code IN (:codes)");
    }
}
