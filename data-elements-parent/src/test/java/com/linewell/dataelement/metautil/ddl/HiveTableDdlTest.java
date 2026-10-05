package com.linewell.dataelement.metautil.ddl;

import static org.assertj.core.api.Assertions.*;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.linewell.dataelement.metautil.explorer.impl.HiveMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.List;
import org.junit.jupiter.api.Test;

class HiveTableDdlTest {
    private ColumnInfo column(String name, String type) {
        var column = new ColumnInfo(); column.setColumnName(name); column.setColumnType(type); return column;
    }
    @Test void sharedExplorerConvertsMysqlTypesAndOmitsRelationalConstraints() {
        var id = column("id", "BIGINT(20) UNSIGNED");
        id.setNullable(false); id.setPrimaryKey(true); id.setAutoIncrement(true); id.setDefaultValue("0");
        var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.HIVE); config.setDatabase("analytics");
        String sql = new HiveMetadataExplorer().generateCreateTableDdl(config, "events", List.of(id,
                column("count", "INT(11)"), column("created", "DATETIME(0)"), column("name", "VARCHAR(255)")), "事件");
        assertThat(sql).contains("`id` DECIMAL(20,0)", "`count` INT", "`created` TIMESTAMP", "`name` STRING", "COMMENT '事件'")
                .doesNotContain("PRIMARY KEY", "NOT NULL", "DEFAULT", "AUTO_INCREMENT", "INT(11)");
        assertThat(new CanvasTableDdl().parse(sql, config).get("lookupName")).isEqualTo("events");
    }
    @Test void convertsOracleSqlServerAndBinaryTypes() {
        String[][] cases = {{"NUMBER(19,0)", "DECIMAL(19,0)"}, {"NUMBER(10,2)", "DECIMAL(10,2)"},
                {"VARCHAR2(60 CHAR)", "STRING"}, {"NVARCHAR2(100)", "STRING"}, {"CLOB", "STRING"},
                {"RAW(2000)", "BINARY"}, {"BLOB", "BINARY"}, {"BYTEA", "BINARY"}, {"DATETIME2(7)", "TIMESTAMP"},
                {"TIMESTAMP(6) WITH TIME ZONE", "STRING"}, {"DOUBLE PRECISION", "DOUBLE"}, {"INTEGER(10) UNSIGNED", "BIGINT"}};
        for (var item : cases) assertThat(HiveTableDdl.type(column("c", item[0]))).as(item[0]).isEqualTo(item[1]);
    }
    @Test void preservesDecimalMetadataWithoutTruncatingUnsupportedPrecision() {
        var decimal = column("amount", "DECIMAL"); decimal.setPrecision(38); decimal.setScale(12);
        assertThat(HiveTableDdl.type(decimal)).isEqualTo("DECIMAL(38,12)");
        decimal.setPrecision(65); assertThat(HiveTableDdl.type(decimal)).isEqualTo("STRING");
        for (String type : List.of("NUMBER", "NUMBER(10,-2)", "DECIMAL(65,30)", "DECIMAL(2,3)"))
            assertThat(HiveTableDdl.type(column("c", type))).as(type).isEqualTo("STRING");
    }
    @Test void preservesNativeComplexTypesAndCommentContent() {
        var value = column("payload", "array<struct<eventId:bigint,label:string>>");
        value.setColumnComment("用户's C:\\data\n第二行");
        String sql = HiveTableDdl.generate("analytics.events", List.of(value), "日志's");
        var parsed = (SQLCreateTableStatement) SQLUtils.parseSingleStatement(sql, DbType.hive);
        assertThat(parsed.getColumnDefinitions()).hasSize(1);
        assertThat(parsed.getColumnDefinitions().get(0).getDataType().toString().toLowerCase()).contains("array", "struct", "eventid");
        assertThat(sql).contains("用户\\'s C:\\\\data\\n第二行");
        assertThat(HiveTableDdl.type(column("c", "mystery_type"))).isEqualTo("STRING");
    }
    @Test void usesOnlyTheTableNameAndKeepsTheTableComment() {
        String sql = HiveTableDdl.generate("analytics.events", List.of(column("id", "BIGINT")), "事件明细");
        assertThat(sql).contains("CREATE TABLE `events`", "COMMENT '事件明细'")
                .doesNotContain("analytics.");
    }
    @Test void rejectsEmptyOrDuplicateFields() {
        assertThatThrownBy(() -> HiveTableDdl.generate("events", List.of(column("ID", "INT"), column("id", "INT")), ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重复字段");
        assertThatThrownBy(() -> HiveTableDdl.generate("", List.of(column("id", "INT")), ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void preservesOracleDateTimeAndFloatPrecision() {
        var date = column("happened", "DATE"); date.setSourceDatabaseType("oceanbaseoracle");
        assertThat(HiveTableDdl.type(date)).isEqualTo("TIMESTAMP");
        date.setSourceDatabaseType("mysql"); assertThat(HiveTableDdl.type(date)).isEqualTo("DATE");
        var number = column("amount", "FLOAT(126)"); number.setSourceDatabaseType("oracle");
        assertThat(HiveTableDdl.type(number)).isEqualTo("STRING");
    }
}
