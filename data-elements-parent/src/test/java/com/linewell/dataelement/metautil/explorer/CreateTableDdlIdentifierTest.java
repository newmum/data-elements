package com.linewell.dataelement.metautil.explorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linewell.dataelement.metautil.ddl.CanvasTableDdl;
import com.linewell.dataelement.metautil.explorer.impl.DamengMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.KingBaseMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.MySqlMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.OracleMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateTableDdlIdentifierTest {

    @Test
    void mongoNamesAreQuotedConsistentlyWithoutChangingOrdinaryOracleCaseFolding() {
        for (DatabaseType type : List.of(DatabaseType.ORACLE, DatabaseType.OCEANBASE_ORACLE, DatabaseType.DAMENG)) {
            var config = new DataSourceConfig(); config.setDatabaseType(type);
            var id = field("_id", "varchar", 24); id.setPrimaryKey(true); id.setNullable(false);
            var timestamp = field("@timestamp", "text", 0);
            var ordinary = field("application_id", "varchar", 64);
            String raw = new OracleMetadataExplorer().generateCreateTableDdl(config, "ODS_VALIDATION", List.of(id,timestamp,ordinary), "现有集合字段");
            String ddl = new com.linewell.dataelement.metautil.ddl.TablePartitions().constraints(raw, List.of(), config);
            assertThat(ddl).contains("\"_id\" VARCHAR2(24 CHAR)", "\"@timestamp\" CLOB", "PRIMARY KEY (\"_id\")",
                    "COMMENT ON COLUMN ODS_VALIDATION.\"_id\"", "COMMENT ON COLUMN ODS_VALIDATION.\"@timestamp\"",
                    "application_id VARCHAR2(64 CHAR)", "COMMENT ON COLUMN ODS_VALIDATION.application_id");
            assertThat(ddl).doesNotContain("\"application_id\"", "PRIMARY KEY (_id)");
        }
    }

    @Test
    void generatedOracleNamesKeepTheirActualSpellingThroughJdbcMetadataAndBindings() throws Exception {
        var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.ORACLE);
        var id = field("_id", "varchar", 24); id.setPrimaryKey(true); id.setNullable(false);
        var timestamp = field("@timestamp", "text", 0);
        var ordinary = field("application_id", "varchar", 64);
        String ddl = new OracleMetadataExplorer().generateCreateTableDdl(config, "ODS_VALIDATION", List.of(id,timestamp,ordinary), "集合字段");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:validation_mongo_identifiers;MODE=Oracle")) {
            try (var sql = connection.createStatement()) {
                for (String statement : com.linewell.dataelement.metautil.ddl.NativeDdlStatements.split(ddl)) sql.execute(statement);
            }
            var names = new java.util.ArrayList<String>();
            try (var columns = connection.getMetaData().getColumns(null, null, "ODS_VALIDATION", null)) {
                while (columns.next()) names.add(columns.getString("COLUMN_NAME"));
            }
            assertThat(names).containsExactly("_id", "@timestamp", "APPLICATION_ID");
            try (var insert = connection.prepareStatement("INSERT INTO ODS_VALIDATION (\"_id\",\"@timestamp\",\"APPLICATION_ID\") VALUES (?,?,?)")) {
                insert.setString(1,"0123456789abcdef01234567"); insert.setString(2,"2026-11-22T00:00:00Z"); insert.setString(3,"validation");
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT \"_id\",\"@timestamp\",APPLICATION_ID FROM ODS_VALIDATION")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("0123456789abcdef01234567");
                assertThat(rows.getString(2)).isEqualTo("2026-11-22T00:00:00Z"); assertThat(rows.getString(3)).isEqualTo("validation");
            }
        }
    }

    private static ColumnInfo field(String name, String type, long length) {
        var field = new ColumnInfo(); field.setColumnName(name); field.setColumnType(type);
        field.setDataType(type); field.setLength(length); field.setColumnComment(name); field.setSourceDatabaseType("mongodb");
        return field;
    }

    @Test
    void usesSeparateLowCodeLengthForMySqlVarchar() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.MYSQL);
        ColumnInfo column = new ColumnInfo();
        column.setColumnName("ODS_UUID");
        column.setColumnType("varchar");
        column.setLength(32L);
        column.setNullable(false);
        column.setColumnComment("ODS主键");

        String ddl = new MySqlMetadataExplorer().generateCreateTableDdl(
            config,
            "ODS_CASE_REPORT",
            List.of(column),
            "案件上报信息"
        );

        assertThat(ddl).contains("ODS_UUID VARCHAR(32) NOT NULL COMMENT 'ODS主键'");
        assertThat(ddl).doesNotContain("ODS_UUID VARCHAR NOT NULL");
    }

    @Test
    void convertsSeparateLowCodeLengthForDamengVarchar() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.DAMENG);
        config.setSchema("ODS");
        ColumnInfo column = new ColumnInfo();
        column.setColumnName("ODS_UUID");
        column.setColumnType("varchar");
        column.setLength(32L);
        column.setNullable(false);
        column.setColumnComment("ODS主键");

        String ddl = new DamengMetadataExplorer().generateCreateTableDdl(
            config,
            "ODS_CASE_REPORT",
            List.of(column),
            "案件上报信息"
        );

        assertThat(ddl).contains("ODS_UUID VARCHAR2(32 CHAR) NOT NULL");
        assertThat(ddl).contains("COMMENT ON COLUMN ODS_CASE_REPORT.ODS_UUID IS 'ODS主键'");
        assertThat(ddl).doesNotContain("ODS_UUID varchar NOT NULL");
    }

    @Test
    void omitsSchemaPrefixFromGeneratedTableAndColumnIdentifiers() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.ORACLE);
        config.setSchema("APP_USER");
        ColumnInfo column = new ColumnInfo();
        column.setColumnName("APP_USER.CASE_ID");
        column.setColumnType("VARCHAR2(32)");
        column.setColumnComment("案件编号");

        String ddl = new OracleMetadataExplorer().generateCreateTableDdl(
            config,
            "APP_USER.CASE_INFO",
            List.of(column),
            "案件信息"
        );

        assertThat(ddl).contains("CREATE TABLE CASE_INFO");
        assertThat(ddl).contains("CASE_ID VARCHAR2(32 CHAR)");
        assertThat(ddl).contains("COMMENT ON TABLE CASE_INFO");
        assertThat(ddl).contains("COMMENT ON COLUMN CASE_INFO.CASE_ID");
        assertThat(ddl).doesNotContain("APP_USER.CASE_INFO");
        assertThat(ddl).doesNotContain("APP_USER.CASE_ID");
    }

    @Test
    void kingbaseMaterializationConvertsOracleTextTypeAndLiteralCastDefault() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.KINGBASE);
        config.setSchema("public");

        ColumnInfo column = new ColumnInfo();
        column.setColumnName("zhbs");
        column.setColumnType("VARCHAR2(32)");
        column.setDefaultValue("'1'::varchar");

        String ddl = new KingBaseMetadataExplorer().generateCreateTableDdl(
                config, "ODS_TEST", List.of(column), null);

        assertThat(ddl).contains("zhbs VARCHAR(32) DEFAULT '1'");
        assertThat(ddl).doesNotContain("VARCHAR2", "::");
        assertThat(new CanvasTableDdl().parse(ddl, config)).containsEntry("lookupName", "ODS_TEST");
    }

    @Test
    void kingbaseAcceptsCompatibleCurrentTimeDefaults() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.KINGBASE);
        ColumnInfo column = new ColumnInfo();
        column.setColumnName("created_at");
        column.setColumnType("timestamp");
        column.setDefaultValue("now()::timestamp");

        assertThat(new KingBaseMetadataExplorer().generateCreateTableDdl(config,"ODS_TEST",List.of(column),null))
                .contains("DEFAULT CURRENT_TIMESTAMP");
    }

    @Test
    void kingbasePreservesPlainStringDefaultContainingDoubleColon() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.KINGBASE);
        ColumnInfo column = new ColumnInfo();
        column.setColumnName("source_ref");
        column.setColumnType("varchar");
        column.setDefaultValue("'system::record'");

        String ddl = new KingBaseMetadataExplorer().generateCreateTableDdl(
                config, "ODS_TEST", List.of(column), null);

        assertThat(ddl).contains("source_ref VARCHAR DEFAULT 'system::record'");
    }
}
