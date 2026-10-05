package com.linewell.dataelement.metautil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TableMetadataCollectionPagingTest {
    private JdbcTemplate jdbc;
    private TableMetadataCollectionService service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:collection_paging;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("drop all objects");
        jdbc.execute("create table db_datasource_t (tid varchar(64), tenant_id varchar(64), is_del int)");
        jdbc.execute("""
                create table db_table_t (
                    tid varchar(64), tenant_id varchar(64), datasource_id varchar(64), is_del int,
                    table_name varchar(255), table_name_en varchar(255), table_name_cn varchar(255),
                    table_comment varchar(255), table_type varchar(30), field_count int, annotated int,
                    created_time timestamp, updated_time timestamp)
                """);
        jdbc.execute("""
                create table metadata_table_collection_job_t (
                    tid varchar(64), tenant_id varchar(64), datasource_id varchar(64), is_del int,
                    status varchar(30), phase varchar(30), collection_mode varchar(30),
                    scanned_count int, persisted_count int, total_count int, added_count int,
                    unchanged_count int, deleted_count int, error_message varchar(255),
                    created_time timestamp, started_time timestamp, finished_time timestamp)
                """);
        jdbc.update("insert into db_datasource_t values ('source', 'tenant_a', 0)");
        jdbc.update("insert into db_datasource_t values ('source', 'tenant_b', 0)");
        service = new TableMetadataCollectionService(jdbc, null, null, null, new ObjectMapper());
    }

    @AfterEach
    void tearDown() { service.shutdown(); TenantContext.clear(); }

    @Test
    void savedTablesAreTenantScopedDeduplicatedSearchedAndDatabasePaged() {
        Timestamp time = Timestamp.from(Instant.parse("2026-10-01T00:00:00Z"));
        for (int index = 1; index <= 30; index++) {
            jdbc.update("insert into db_table_t values (?, 'tenant_a', 'source', 0, ?, ?, ?, ?, 'TABLE', ?, 0, ?, ?)",
                    "table-" + index, String.format("table_%02d", index), String.format("table_%02d", index),
                    "表" + index, "说明" + index, index, time, time);
        }
        jdbc.update("insert into db_table_t values ('duplicate', 'tenant_a', 'source', 0, 'table_01', 'table_01', '已登记', '已登记', 'TABLE', 99, 1, ?, ?)", time, time);
        jdbc.update("insert into db_table_t values ('other', 'tenant_b', 'source', 0, 'secret', 'secret', 'secret', 'secret', 'TABLE', 1, 0, ?, ?)", time, time);

        TenantContext.call("tenant_a", () -> {
            Map<String, Object> first = service.savedTables("source", 1, 15, "");
            Map<String, Object> second = service.savedTables("source", 2, 15, "");
            assertThat(first.get("total")).isEqualTo(30L);
            assertThat((List<?>) first.get("rows")).hasSize(15);
            assertThat((List<?>) second.get("rows")).hasSize(15);
            assertThat(((Map<?, ?>) ((List<?>) first.get("rows")).getFirst()).get("fieldCount")).isEqualTo(99L);
            Map<String, Object> search = service.savedTables("source", 1, 15, "说明29");
            assertThat(search.get("total")).isEqualTo(1L);
            assertThat((List<?>) search.get("rows")).hasSize(1);
            return null;
        });
        TenantContext.call("tenant_b", () -> {
            assertThat(service.savedTables("source", 1, 15, "").get("total")).isEqualTo(1L);
            return null;
        });
    }

    @Test
    void latestRunAdditionsReturnOnlyRowsCreatedDuringThatRun() {
        Timestamp before = Timestamp.from(Instant.parse("2026-10-01T00:00:00Z"));
        Timestamp start = Timestamp.from(Instant.parse("2026-10-01T01:00:00Z"));
        Timestamp added = Timestamp.from(Instant.parse("2026-10-01T01:01:00Z"));
        Timestamp finish = Timestamp.from(Instant.parse("2026-10-01T01:02:00Z"));
        jdbc.update("insert into metadata_table_collection_job_t values ('job', 'tenant_a', 'source', 0, 'SUCCEEDED', 'COMPLETED', 'REFRESH', 2, 1, 2, 1, 1, 0, '', ?, ?, ?)", start, start, finish);
        jdbc.update("insert into db_table_t values ('old', 'tenant_a', 'source', 0, 'old', 'old', '旧表', '旧表', 'TABLE', 2, 0, ?, ?)", before, before);
        jdbc.update("insert into db_table_t values ('new', 'tenant_a', 'source', 0, 'new', 'new', '新表', '新表', 'TABLE', 3, 0, ?, ?)", added, added);

        TenantContext.call("tenant_a", () -> {
            Map<String, Object> result = service.addedTables("source", 1, 15);
            assertThat(result.get("total")).isEqualTo(1L);
            assertThat(((Map<?, ?>) ((List<?>) result.get("rows")).getFirst()).get("tableName")).isEqualTo("new");
            Map<String, Object> status = service.status(null, "source", false);
            assertThat(status.get("savedBeforeCount")).isEqualTo(1L);
            assertThat(status.get("finishedAt")).isEqualTo(finish);
            return null;
        });
    }

    @Test
    void batchStatusReturnsLatestJobsInInputOrderWithoutCrossTenantRows() {
        jdbc.update("insert into db_datasource_t values ('source-two', 'tenant_a', 0)");
        jdbc.update("insert into db_datasource_t values ('foreign', 'tenant_b', 0)");
        Timestamp old = Timestamp.from(Instant.parse("2026-10-01T01:00:00Z"));
        Timestamp recent = Timestamp.from(Instant.parse("2026-10-02T01:00:00Z"));
        jdbc.update("insert into metadata_table_collection_job_t values ('old', 'tenant_a', 'source', 0, 'SUCCEEDED', 'COMPLETED', 'INITIAL', 3, 3, 3, 3, 0, 0, '', ?, ?, ?)", old, old, old);
        jdbc.update("insert into metadata_table_collection_job_t values ('latest', 'tenant_a', 'source', 0, 'FAILED', 'FAILED', 'REFRESH', 1, 0, 3, 0, 0, 0, '失败', ?, ?, ?)", recent, recent, recent);
        jdbc.update("insert into metadata_table_collection_job_t values ('foreign-job', 'tenant_b', 'source', 0, 'SUCCEEDED', 'COMPLETED', 'INITIAL', 100, 100, 100, 100, 0, 0, '', ?, ?, ?)", recent, recent, recent);

        TenantContext.call("tenant_a", () -> {
            Map<String, Object> result = service.batchStatus(List.of("source-two", "source"));
            List<?> items = (List<?>) result.get("items");
            assertThat(items).hasSize(2);
            assertThat(((Map<?, ?>) items.get(0)).get("status")).isEqualTo("NOT_COLLECTED");
            assertThat(((Map<?, ?>) items.get(1)).get("jobId")).isEqualTo("latest");
            assertThat(((Map<?, ?>) items.get(1)).get("previousSuccessfulAt")).isEqualTo(old);
            assertThat(((Map<?, ?>) items.get(1)).get("totalCount")).isEqualTo(3L);
            assertThatThrownBy(() -> service.batchStatus(List.of("foreign")))
                    .isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }
}
