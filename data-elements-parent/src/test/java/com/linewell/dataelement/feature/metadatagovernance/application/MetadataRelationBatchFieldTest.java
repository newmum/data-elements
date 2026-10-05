package com.linewell.dataelement.feature.metadatagovernance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MetadataRelationBatchFieldTest {
    private CountingJdbcTemplate jdbc;
    private MetadataGovernanceService service;

    @BeforeEach
    void setUp() {
        jdbc = new CountingJdbcTemplate();
        jdbc.execute("drop all objects");
        jdbc.execute("create table db_table_t (tid varchar(64), tenant_id varchar(64), is_del int)");
        jdbc.execute("create table db_table_column_t (tid varchar(64), tenant_id varchar(64), table_id varchar(64), is_del int, column_name varchar(64))");
        jdbc.update("insert into db_table_t values ('s','tenant-a',0),('t','tenant-a',0),('s','tenant-b',0)");
        jdbc.update("insert into db_table_column_t values ('s1','tenant-a','s',0,'source_one'),('s2','tenant-a','s',0,'source_two'),('t1','tenant-a','t',0,'target_one'),('t2','tenant-a','t',0,'target_two'),('foreign','tenant-b','s',0,'private')");
        service = new MetadataGovernanceService(jdbc);
    }

    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void validatesAllMappingsAndConditionsInOneTenantScopedQuery() {
        TenantContext.call("tenant-a", () -> {
            Map<String,Object> request = Map.of("sourceTableId","s","targetTableId","t",
                    "mappings",List.of(Map.of("sourceColumnId","s1","targetColumnId","t1"),Map.of("sourceColumnId","s2","targetColumnId","t2")),
                    "conditions",List.of(Map.of("side","source","columnId","s1","operator","is_not_null"),Map.of("side","target","columnId","t2","operator","eq","value",1)));
            assertThat((List<?>)service.prepareDefinition(request).get("mappings")).hasSize(2);
            assertThat(jdbc.fieldReads).isEqualTo(1);
            assertThatThrownBy(() -> service.prepareDefinition(Map.of("sourceTableId","s","targetTableId","t","sourceColumnId","foreign","targetColumnId","t1")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不存在");
            assertThat(jdbc.fieldReads).isEqualTo(2);
            return null;
        });
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        int fieldReads;
        CountingJdbcTemplate() { super(new DriverManagerDataSource("jdbc:h2:mem:metadata_relation_batch;DB_CLOSE_DELAY=-1", "sa", "")); }
        @Override public List<Map<String,Object>> queryForList(String sql,Object... args) {
            if(sql.contains("FROM db_table_column_t c")) fieldReads++;
            return super.queryForList(sql,args);
        }
    }
}
