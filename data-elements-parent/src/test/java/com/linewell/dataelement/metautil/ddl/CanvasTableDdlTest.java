package com.linewell.dataelement.metautil.ddl;

import static org.assertj.core.api.Assertions.*;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class CanvasTableDdlTest {
    private final CanvasTableDdl parser = new CanvasTableDdl();
    private DataSourceConfig config(DatabaseType type) {
        var config = new DataSourceConfig();
        config.setDatabaseType(type);
        config.setDatabase("analytics");
        config.setSchema("analytics");
        config.setUsername("app@tenant#cluster");
        return config;
    }
    @Test void parsesMysqlWithoutLettingIfNotExistsHideADuplicate() {
        var result = parser.parse("CREATE TABLE IF NOT EXISTS `events` (id BIGINT PRIMARY KEY, note VARCHAR(100) COMMENT 'a;b') ENGINE=InnoDB;", config(DatabaseType.MYSQL));
        assertThat(result.get("lookupName")).isEqualTo("events");
        assertThat(result.get("ddl").toString()).doesNotContain("IF NOT EXISTS").contains("a;b");
    }
    @Test void parsesDmAndOceanBaseOracleWithRelatedComments() {
        for (var type : new DatabaseType[]{DatabaseType.DAMENG, DatabaseType.OCEANBASE_ORACLE}) {
            var result = parser.parse("CREATE TABLE analytics.EVENTS (ID NUMBER(19) NOT NULL, PRIMARY KEY(ID)); COMMENT ON TABLE analytics.EVENTS IS '事件'; COMMENT ON COLUMN analytics.EVENTS.ID IS '编号';", config(type));
            assertThat(result.get("tableName")).isEqualTo("EVENTS");
            assertThat(result.get("ddl").toString()).contains("COMMENT ON COLUMN");
        }
    }
    @Test void keepsHiveDdlAtTheTableLevelWhileRecordingTheSelectedDatabase() {
        var result = parser.parse("CREATE TABLE events (id BIGINT, data STRING) PARTITIONED BY (dt STRING) STORED AS ORC", config(DatabaseType.HIVE));
        assertThat(result.get("ddl").toString()).contains("CREATE TABLE events").contains("PARTITIONED BY")
                .doesNotContain("analytics.");
        assertThat(result.get("schemaName")).isEqualTo("analytics");
    }
    @Test void rejectsOtherStatementsCrossDatabaseAndUnrelatedComments() {
        for (String ddl : new String[]{"DROP TABLE events", "CREATE TABLE other.events (id INT)",
                "CREATE TABLE events(id INT); DELETE FROM events", "CREATE TABLE events(id INT); CREATE TABLE more(id INT)",
                "CREATE TABLE events AS SELECT 1 id", "CREATE TABLE events(id INT); COMMENT ON TABLE other IS 'x'",
                "CREATE OR REPLACE TABLE events(id INT)"}) {
            assertThatThrownBy(() -> parser.parse(ddl, config(DatabaseType.MYSQL))).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void checksExactNamesAndRecognizesDuplicateDatabaseErrors() {
        var parsed = parser.parse("CREATE TABLE events(id INT)", config(DatabaseType.MYSQL));
        assertThat(parser.matches(parsed, "events", "analytics")).isTrue();
        assertThat(parser.matches(parsed, "events_archive", "analytics")).isFalse();
        assertThat(parser.matches(parsed, "events", "other")).isFalse();
        assertThat(parser.alreadyExists(new RuntimeException(new SQLException("ORA-00955", "42000", 955)))).isTrue();
        assertThat(parser.alreadyExists(new SQLException("permission denied", "42000", 1044))).isFalse();
    }
}
