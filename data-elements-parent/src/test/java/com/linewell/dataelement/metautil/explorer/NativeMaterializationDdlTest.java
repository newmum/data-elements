package com.linewell.dataelement.metautil.explorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linewell.dataelement.metautil.explorer.impl.HiveMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.MySqlMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Exercises the public, connection-free DDL path used by materialization preview. */
class NativeMaterializationDdlTest {
    @ParameterizedTest
    @CsvSource({"MYSQL,VARBINARY(16),LONGBLOB", "MARIADB,VARBINARY(16),LONGBLOB",
            "OCEANBASE_MYSQL,VARBINARY(16),LONGBLOB", "ORACLE,RAW(16),BLOB",
            "OCEANBASE_ORACLE,RAW(16),BLOB", "DAMENG,RAW(16),BLOB",
            "POSTGRESQL,BYTEA,BYTEA", "KINGBASE,BYTEA,BYTEA", "GAUSSDB,BYTEA,BYTEA",
            "HIGHGO,BYTEA,BYTEA", "SQLSERVER,VARBINARY(16),VARBINARY(MAX)", "HETU,VARBINARY,VARBINARY"})
    void binaryTemplateUsesTheActualTargetDialect(DatabaseType type, String binaryType, String lobType) {
        var config = config(type);
        var key = column("binary_id", "varbinary", 16);
        key.setPrimaryKey(true);
        String ddl = new MySqlMetadataExplorer().generateCreateTableDdl(config, "ods_native",
                List.of(key, column("payload", "blob", 0)), null);
        assertThat(ddl).contains("binary_id " + binaryType, "payload " + lobType, "PRIMARY KEY (binary_id)");
    }

    @Test
    void hiveCanonicalBinaryAndFloatingTypesRemainBinaryAndFloating() {
        String ddl = new HiveMetadataExplorer().generateCreateTableDdl(config(DatabaseType.HIVE), "ods_native",
                List.of(column("binary_id", "varbinary", 16), column("payload", "blob", 0),
                        column("measure", "float", 0), column("reading", "double", 0)), null);
        assertThat(ddl).contains("`binary_id` BINARY", "`payload` BINARY", "`measure` FLOAT", "`reading` DOUBLE");
    }

    @Test
    void floatingTypesKeepTheirRangeAndUuidKeepsTextLength() {
        for (var type : List.of(DatabaseType.ORACLE, DatabaseType.POSTGRESQL, DatabaseType.SQLSERVER, DatabaseType.MYSQL)) {
            String ddl = new MySqlMetadataExplorer().generateCreateTableDdl(config(type), "ods_native",
                    List.of(column("measure", "float", 0), column("reading", "double", 0), column("uuid", "varchar", 36)), null);
            String single = type == DatabaseType.ORACLE ? "BINARY_FLOAT" : type == DatabaseType.SQLSERVER ? "REAL" : "FLOAT";
            String dual = type == DatabaseType.ORACLE ? "BINARY_DOUBLE" : type == DatabaseType.POSTGRESQL ? "DOUBLE PRECISION"
                    : type == DatabaseType.SQLSERVER ? "FLOAT(53)" : "DOUBLE";
            assertThat(ddl).contains("measure " + single, "reading " + dual,
                    "uuid " + (type == DatabaseType.ORACLE ? "VARCHAR2(36 CHAR)" : type==DatabaseType.SQLSERVER?"NVARCHAR(72)":"VARCHAR(36)"));
        }
    }

    @Test
    void largeBinaryDoesNotGetTruncatedAndOraclePrimaryKeyIsNotSilentlyConvertedToLob() {
        var data = column("raw_data", "varbinary", 9000);
        assertThat(new MySqlMetadataExplorer().generateCreateTableDdl(config(DatabaseType.SQLSERVER), "ods_native", List.of(data), null))
                .contains("raw_data VARBINARY(MAX)");
        assertThat(new MySqlMetadataExplorer().generateCreateTableDdl(config(DatabaseType.ORACLE), "ods_native", List.of(data), null))
                .contains("raw_data BLOB");
        data.setPrimaryKey(true);
        assertThat(new MySqlMetadataExplorer().generateCreateTableDdl(config(DatabaseType.ORACLE), "ods_native", List.of(data), null))
                .contains("raw_data BLOB", "PRIMARY KEY");
        var preview=new com.linewell.dataelement.metautil.ddl.PortableDdlConverter().prepare(List.of(data),DatabaseType.ORACLE);
        assertThat((List<?>)preview.get("warnings")).isNotEmpty();
    }

    private static DataSourceConfig config(DatabaseType type) {
        var config = new DataSourceConfig();
        config.setDatabaseType(type);
        if (type == DatabaseType.DAMENG) config.setSchema("ODS");
        return config;
    }

    @Test
    void genuineLongTextRemainsEditableAndShowsTargetKeyWarnings() {
        for (var type : List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE,DatabaseType.DAMENG)) {
            var text=column("UNID","text(65535)",0); text.setPrimaryKey(true);
            assertThat(new MySqlMetadataExplorer().generateCreateTableDdl(config(type),"ODS_APAS",List.of(text),null))
                    .contains("UNID CLOB", "PRIMARY KEY");
            assertThat((List<?>)new com.linewell.dataelement.metautil.ddl.PortableDdlConverter().prepare(List.of(text),type).get("warnings"))
                    .isNotEmpty();
        }
    }

    @Test
    void sourceLiteralCastsDoNotReachForeignDdlAndQuotedDoubleColonIsPreserved() {
        for (var type : List.of(DatabaseType.ORACLE, DatabaseType.OCEANBASE_ORACLE, DatabaseType.DAMENG, DatabaseType.MYSQL)) {
            var field = column("enabled", "varchar", 1);
            field.setDefaultValue("'Y'::varchar");
            assertThat(new MySqlMetadataExplorer().generateCreateTableDdl(config(type), "ODS_FLAGS", List.of(field), null))
                    .contains("DEFAULT 'Y'").doesNotContain("::");
            field.setDefaultValue("'system::record'");
            assertThat(new MySqlMetadataExplorer().generateCreateTableDdl(config(type), "ODS_FLAGS", List.of(field), null))
                    .contains("DEFAULT 'system::record'");
        }
    }

    private static ColumnInfo column(String name, String type, long length) {
        var column = new ColumnInfo();
        column.setColumnName(name);
        column.setColumnType(type);
        column.setLength(length);
        return column;
    }
}
