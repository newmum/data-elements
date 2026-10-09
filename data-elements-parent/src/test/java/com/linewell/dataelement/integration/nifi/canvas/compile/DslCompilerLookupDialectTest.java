package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DslCompilerLookupDialectTest {
    @ParameterizedTest
    @ValueSource(strings = {"POSTGRESQL", "postgres", "pg", "GAUSSDB", "openGauss",
            "kingbase8", "HIGHGO", "TDSQL_PG", "hailiang", "MYSQL", "MARIADB", "SQLSERVER"})
    void singleAndGroupedQueriesUseTheDictionaryDialectWithoutDual(String type) throws Exception {
        var first = lookup("FirstLabel_cn", Map.of("dbType", type));
        var second = lookup("SecondLabel_cn", Map.of("dbType", type));

        String single = singleQuery(first);
        String grouped = query(List.of(first, second));

        assertThat(single).isEqualTo(query(List.of(first))).doesNotContain("FROM DUAL");
        assertThat(grouped).doesNotContain("FROM DUAL");
        assertThat(grouped.split(" LEFT JOIN ")).hasSize(3);
    }

    @ParameterizedTest
    @CsvSource({"ORACLE,FROM DUAL", "DM,FROM DUAL", "DAMENG,FROM DUAL",
            "OCEANBASE_ORACLE,FROM DUAL", "DB2,FROM SYSIBM.SYSDUMMY1",
            "GBASE8S,FROM systables WHERE tabid = 1"})
    void retainsEachDatabaseSpecificSingleRowSource(String type, String from) throws Exception {
        var lookup = lookup("Label_cn", Map.of("dbType", type));
        assertThat(query(List.of(lookup))).contains(from);
        assertThat(singleQuery(lookup)).isEqualTo(query(List.of(lookup)));
    }

    @Test
    void respectsOceanBaseCompatibilityModeAndLegacyJdbcTypeInformation() throws Exception {
        assertThat(query(List.of(lookup("Label_cn", Map.of(
                "dbType", "oceanbase", "compatibleMode", "ORACLE"))))).contains("FROM DUAL");
        assertThat(query(List.of(lookup("Label_cn", Map.of(
                "dbType", "oceanbase", "compatibleMode", "MYSQL"))))).doesNotContain("FROM DUAL");
        assertThat(query(List.of(lookup("Label_cn", Map.of(
                "jdbcURL", "jdbc:postgresql://example.test/dictionary")))))
                .doesNotContain("FROM DUAL").contains("AS \"ODS_UUID\"", "AS \"Label_cn\"");
    }

    @Test
    void rejectsAnUnknownDictionaryTypeInsteadOfGuessingItsDialect() {
        assertThatThrownBy(() -> query(List.of(lookup("Label_cn", Map.of("dbType", "unknown-vendor")))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("数据库类型不受支持");
    }

    @ParameterizedTest
    @CsvSource({"POSTGRESQL,PostgreSQL", "ORACLE,Oracle", "MYSQL,MySQL"})
    void executesGroupedLookupAndPreservesPassThroughAndMissingValues(String type, String mode) throws Exception {
        // These are isolated H2 dialect-mode executions, not a claim of vendor-server acceptance.
        String url = "jdbc:h2:mem:lookup_" + UUID.randomUUID()
                + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url)) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE dict_codes (code VARCHAR(32), label VARCHAR(64))");
                statement.execute("INSERT INTO dict_codes VALUES ('01', '刑事案件'), ('02', '行政案件')");
            }
            String sql = query(List.of(lookup("FirstLabel_cn", Map.of("dbType", type)),
                    lookup("SecondLabel_cn", Map.of("dbType", type))));
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, "original-id");
                statement.setString(2, "2026-10-09 12:00:00");
                statement.setString(3, "01");
                statement.setString(4, "not-present");
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("ODS_UUID")).isEqualTo("original-id");
                    assertThat(rows.getString("TIME")).isEqualTo("2026-10-09 12:00:00");
                    assertThat(rows.getString("FirstLabel_cn")).isEqualTo("刑事案件");
                    assertThat(rows.getObject("SecondLabel_cn")).isNull();
                    if ("POSTGRESQL".equals(type)) {
                        assertThat(rows.getMetaData().getColumnLabel(1)).isEqualTo("ODS_UUID");
                        assertThat(rows.getMetaData().getColumnLabel(3)).isEqualTo("FirstLabel_cn");
                    }
                    assertThat(rows.next()).isFalse();
                }
                statement.setString(1, "");
                statement.setString(3, "not-present");
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject("ODS_UUID")).isNull();
                    assertThat(rows.getObject("FirstLabel_cn")).isNull();
                    assertThat(rows.getObject("SecondLabel_cn")).isNull();
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    private static FieldMappingService.LookupPlan lookup(String target, Map<String, Object> config) {
        return new FieldMappingService.LookupPlan(target, target,
                "SELECT label AS " + target + " FROM dict_codes WHERE code = ?",
                List.of("code"), List.of("__lookup_" + target), "NULL", config);
    }

    private static String query(List<FieldMappingService.LookupPlan> lookups) throws Exception {
        return invoke(List.class, lookups);
    }

    private static String singleQuery(FieldMappingService.LookupPlan lookup) throws Exception {
        return invoke(FieldMappingService.LookupPlan.class, lookup);
    }

    private static String invoke(Class<?> argumentType, Object lookup) throws Exception {
        Method method = DslCompiler.class.getDeclaredMethod("buildLookupEnrichmentQuery", argumentType, List.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(new DslCompiler(null, null, null, null), lookup, List.of("ODS_UUID", "TIME"));
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) throw cause;
            throw ex;
        }
    }
}
