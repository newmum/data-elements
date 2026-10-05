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

        assertThat(ddl).contains("ODS_UUID VARCHAR2(32) NOT NULL");
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
        assertThat(ddl).contains("CASE_ID VARCHAR2(32)");
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
    void kingbaseRejectsComplexSourceCastWithColumnName() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.KINGBASE);
        ColumnInfo column = new ColumnInfo();
        column.setColumnName("created_at");
        column.setColumnType("timestamp");
        column.setDefaultValue("now()::timestamp");

        assertThatThrownBy(() -> new KingBaseMetadataExplorer().generateCreateTableDdl(
                config, "ODS_TEST", List.of(column), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("created_at")
                .hasMessageContaining("默认值");
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
