package com.linewell.dataelement.dataassets.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MetadataAssetPersistenceModuleTest {

    @Test
    void quotesReservedColumnNamesAndFillsCurrentTenantDuringBatchInsert() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:metadata_asset;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE db_table_column_t (
                  "tenant_id" VARCHAR(64), "charset" VARCHAR(64), "created_time" TIMESTAMP,
                  "updated_time" TIMESTAMP, "source_table_column_id" VARCHAR(64), "nullable" INTEGER,
                  "auto_increment" INTEGER, "indexed" INTEGER, "column_name" VARCHAR(128),
                  "length" BIGINT, "scale" INTEGER, "is_unique" INTEGER, "default_value" VARCHAR(255),
                  "table_id" VARCHAR(64), "primary_key" INTEGER, "tid" VARCHAR(64) PRIMARY KEY,
                  "precision_length" INTEGER, "ordinal_position" INTEGER, "extra" VARCHAR(255),
                  "column_comment" CLOB, "data_type" VARCHAR(64), "is_del" INTEGER,
                  "collation" VARCHAR(128), "column_type" VARCHAR(128)
                )
                """);

        Timestamp now = Timestamp.from(Instant.now());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenant_id", "stale-tenant");
        row.put("charset", "utf8mb4");
        row.put("created_time", now);
        row.put("updated_time", now);
        row.put("source_table_column_id", "source-column");
        row.put("nullable", true);
        row.put("auto_increment", false);
        row.put("indexed", true);
        row.put("column_name", "person_id");
        row.put("length", 64L);
        row.put("scale", 0);
        row.put("is_unique", true);
        row.put("default_value", null);
        row.put("table_id", "target-table");
        row.put("primary_key", true);
        row.put("tid", "target-column");
        row.put("precision_length", 64);
        row.put("ordinal_position", 1);
        row.put("extra", "");
        row.put("column_comment", "人员标识");
        row.put("data_type", "varchar");
        row.put("is_del", 0);
        row.put("collation", "utf8mb4_general_ci");
        row.put("column_type", "varchar(64)");

        MetadataAssetPersistenceModule module = new MetadataAssetPersistenceModule(dataSource);
        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            assertThat(module.batchInsertTableColumns(java.util.List.of(row))).isEqualTo(1);
        }

        Map<String, Object> saved = jdbc.queryForMap(
                "SELECT \"tenant_id\", \"auto_increment\", \"collation\", \"column_comment\" "
                        + "FROM db_table_column_t WHERE \"tid\" = ?",
                "target-column");
        assertThat(((Number) saved.get("auto_increment")).intValue()).isZero();
        assertThat(saved.get("tenant_id")).isEqualTo("tenant-a");
        assertThat(saved.get("collation")).isEqualTo("utf8mb4_general_ci");
        assertThat(saved.get("column_comment")).hasToString("人员标识");
    }
}
