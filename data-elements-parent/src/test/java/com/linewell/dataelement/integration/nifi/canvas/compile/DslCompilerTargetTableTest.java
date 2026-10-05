package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DslCompilerTargetTableTest {

    @Test
    void normalizesOceanBaseOracleTableForNifiMetadataLookup() {
        Map<String, Object> config = new LinkedHashMap<>(Map.of(
                "dbType", "OCEANBASE_ORACLE",
                "table", "ui_component_t"));

        DslCompiler.normalizeJdbcSinkTableName(config);

        assertThat(config.get("table")).isEqualTo("UI_COMPONENT_T");
    }

    @Test
    void qualifiesConfiguredOracleSchema() {
        Map<String, Object> config = new LinkedHashMap<>(Map.of(
                "dbType", "Oracle",
                "defaultSchema", "ods_owner",
                "table", "sym_dict_t"));

        DslCompiler.normalizeJdbcSinkTableName(config);

        assertThat(config.get("table")).isEqualTo("ODS_OWNER.SYM_DICT_T");
    }

    @Test
    void normalizesOracleSourceOwnerAndSidConnection() {
        Map<String, Object> config = new LinkedHashMap<>(Map.of(
                "dbType", "Oracle",
                "host", "10.130.46.1",
                "port", "1521",
                "connectionType", "SID",
                "sid", "ORCL",
                "defaultSchema", "app_owner",
                "table", "t_jyz_yjxx_new"));

        DslCompiler.normalizeJdbcSourceConfig(config);

        assertThat(config.get("table")).isEqualTo("APP_OWNER.T_JYZ_YJXX_NEW");
        assertThat(config.get("jdbcUrl")).isEqualTo("jdbc:oracle:thin:@10.130.46.1:1521:ORCL");
    }

    @Test
    void normalizesRegisteredOracleServiceNameIntoTheCanvasEndpointContract() {
        Map<String, Object> config = new LinkedHashMap<>(Map.of(
                "dbType", "Oracle",
                "host", "10.130.46.1",
                "port", "1521",
                "connectionType", "SERVICE_NAME",
                "serviceName", "ORCLPDB1",
                "table", "orders"));

        DslCompiler.normalizeJdbcSourceConfig(config);

        assertThat(config.get("sid")).isEqualTo("ORCLPDB1");
        assertThat(config.get("database")).isEqualTo("ORCLPDB1");
        assertThat(config.get("jdbcUrl")).isEqualTo("jdbc:oracle:thin:@//10.130.46.1:1521/ORCLPDB1");
    }

    @Test
    void usesLegacyOracleFetchDialectForEveryOracleVersion() {
        assertThat(DslCompiler.sourceFetchDatabaseType(new LinkedHashMap<>(Map.of(
                "dbType", "Oracle")))).isEqualTo("Oracle");
    }

    @Test
    void ignoresTheFormerOracleFetchDialectSetting() {
        assertThat(DslCompiler.sourceFetchDatabaseType(new LinkedHashMap<>(Map.of(
                "dbType", "Oracle",
                "oracleFetchDialect", "ORACLE_12_PLUS")))).isEqualTo("Oracle");
    }

    @Test
    void normalizesOceanBaseOracleModeBeforeGeneratingFetchSql() {
        Map<String, Object> config = new LinkedHashMap<>(Map.of(
                "dbType", "OceanBase",
                "compatibleMode", "ORACLE",
                "host", "10.130.46.2",
                "port", "2881",
                "database", "APPDB",
                "table", "orders"));

        DslCompiler.normalizeJdbcSourceConfig(config);

        assertThat(config.get("dbType")).isEqualTo("OCEANBASE_ORACLE");
        assertThat(config.get("table")).isEqualTo("ORDERS");
        assertThat(config.get("jdbcUrl")).isEqualTo("jdbc:oceanbase:oracle://10.130.46.2:2881/APPDB");
        assertThat(DslCompiler.sourceFetchDatabaseType(config)).isEqualTo("Oracle");
    }

    @Test
    void rewritesOracleDateWatermarksUsingExplicitToDate() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("dbType", "Oracle");
        config.put("incrementalColumn", "YJSJ");
        config.put("sourceColumns", java.util.List.of(Map.of("columnName", "YJSJ", "dataType", "DATE")));

        DslCompiler.configureOracleCompatibleFetchSql(config);

        assertThat(config.get("oracleWatermarkSearch")).isEqualTo(
                "(?i)timestamp\\s+'(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?'");
        assertThat(config.get("oracleWatermarkReplacement")).isEqualTo(
                "TO_DATE('$1', 'YYYY-MM-DD HH24:MI:SS')");
        String generatedSql = "SELECT * FROM TC_SZPT.T_JYZ_YJXX_NEW "
                + "WHERE YJSJ > timestamp '2026-08-18 18:02:21.0' "
                + "AND YJSJ <= timestamp '2026-08-18 18:07:23.0' "
                + "ORDER BY YJSJ";
        String normalizedSql = Pattern.compile(String.valueOf(config.get("oracleWatermarkSearch")))
                .matcher(generatedSql)
                .replaceAll(String.valueOf(config.get("oracleWatermarkReplacement")));
        assertThat(normalizedSql).isEqualTo("SELECT * FROM TC_SZPT.T_JYZ_YJXX_NEW "
                + "WHERE YJSJ > TO_DATE('2026-08-18 18:02:21', 'YYYY-MM-DD HH24:MI:SS') "
                + "AND YJSJ <= TO_DATE('2026-08-18 18:07:23', 'YYYY-MM-DD HH24:MI:SS') "
                + "ORDER BY YJSJ");
    }

    @Test
    void preservesFractionalSecondsForOracleTimestampWatermarks() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("dbType", "OCEANBASE_ORACLE");
        config.put("incrementalColumn", "UPDATE_TIME");
        config.put("sourceColumns", java.util.List.of(Map.of(
                "columnName", "UPDATE_TIME", "dataType", "TIMESTAMP(6)")));

        DslCompiler.configureOracleCompatibleFetchSql(config);

        assertThat(config.get("oracleWatermarkReplacement")).isEqualTo(
                "TO_TIMESTAMP('$1', 'YYYY-MM-DD HH24:MI:SS.FF')");
    }

    @Test
    void keepsOceanBaseMySqlModeOnItsExistingDialect() {
        assertThat(DslCompiler.sourceFetchDatabaseType(new LinkedHashMap<>(Map.of(
                "dbType", "OceanBase",
                "compatibleMode", "MYSQL")))).isNull();
    }

    @Test
    void keepsMySqlTableNameUnchanged() {
        Map<String, Object> config = new LinkedHashMap<>(Map.of(
                "dbType", "MySQL",
                "table", "ui_component_t"));

        DslCompiler.normalizeJdbcSinkTableName(config);

        assertThat(config.get("table")).isEqualTo("ui_component_t");
    }

    @Test
    void appendsOracleDriverPropertiesToNifiDbcpService() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("jdbcProperties", Map.of(
                "oracle.jdbc.encryption_client", "required",
                "oracle.jdbc.encryption_algorithms_client", "(AES256,AES192,AES128)"));
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("Database User", "scott");

        DslCompiler.appendJdbcDriverProperties("org.apache.nifi.dbcp.DBCPConnectionPool", config, properties);

        assertThat(properties).containsEntry("oracle.jdbc.encryption_client", "required")
                .containsEntry("oracle.jdbc.encryption_algorithms_client", "(AES256,AES192,AES128)")
                .containsEntry("Database User", "scott");
    }
}
