package com.linewell.dataelement.integration.nifi.canvas.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiValueDictionarySqlTest {
    @Test
    void standardQueryKeepsOrderedTokensAndEscapesLabels() {
        var values = new LinkedHashMap<String, String>();
        values.put("U", "未知");
        values.put("F", "女'性");
        String sql = MultiValueDictionarySql.standard("gender_code_cn", values, ",");

        assertThat(sql).startsWith("SELECT CASE WHEN SUM(")
                .contains("JSON_TABLE(CONCAT('[', REPLACE(JSON_QUOTE(?), ',', CHAR(34,44,34))")
                .contains("ORDER BY tokens.ord SEPARATOR ','")
                .contains("SELECT 'F' AS dict_code, '女''性' AS dict_name")
                .contains("AS gender_code_cn");
    }

    @Test
    void rejectsAmbiguousSeparatorAndUnsafeTarget() {
        assertThrows(IllegalStateException.class,
                () -> MultiValueDictionarySql.standard("gender_code_cn", java.util.Map.of("U", "未知"), "\""));
        assertThrows(IllegalStateException.class,
                () -> MultiValueDictionarySql.standard("gender-code", java.util.Map.of("U", "未知"), ","));
    }

    @Test
    void generatedRuleCompilesAsVisibleNifiLookupRatherThanSourcePushdown() {
        String sql = MultiValueDictionarySql.standard("gender_code_cn", Map.of("U", "未知", "F", "女"), ",");
        Map<String, Object> lookup = Map.of("sql", sql, "resultColumn", "gender_code_cn",
                "multiValue", true, "dataSource", Map.of("dbType", "MYSQL"));
        Map<String, Object> rule = Map.of("from", "/gender_code", "to", "/gender_code_cn", "lookup", lookup);
        Map<String, Object> spec = Map.of("version", "1.0", "mappings", java.util.List.of(rule));

        FieldMappingService mapping = new FieldMappingService();
        var plan = mapping.compilePlan(spec);

        assertThat(plan.lookups()).hasSize(1);
        assertThat(plan.lookups().getFirst().multiValue()).isTrue();
        assertThat(plan.lookups().getFirst().parameterFields()).containsExactly("gender_code");
        assertThat(mapping.supportsSourceDbPushdown(spec)).isFalse();
    }
}
