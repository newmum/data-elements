package com.linewell.dataelement.feature.metadatagovernance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MetadataCatalogPageTest {
    private JdbcTemplate jdbc;
    private MetadataGovernanceService service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:metadata_catalog_page;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("drop all objects");
        jdbc.execute("create table db_datasource_t (tid varchar(64), tenant_id varchar(64), is_del int, db_name varchar(100))");
        jdbc.execute("create table db_table_t (tid varchar(64), tenant_id varchar(64), datasource_id varchar(64), is_del int, table_name varchar(100), table_name_cn varchar(100), table_comment varchar(100), table_type varchar(30), field_count int, updated_time timestamp)");
        jdbc.execute("create table db_table_column_t (tid varchar(64), tenant_id varchar(64), table_id varchar(64), is_del int, column_name varchar(100), column_comment varchar(100), data_type varchar(30), column_type varchar(30), length int, nullable int, primary_key int, is_unique int, indexed int, ordinal_position int)");
        jdbc.execute("create table da_catalog_t (tid varchar(64), tenant_id varchar(64), is_del int, catalog_name varchar(100), catalog_name_en varchar(100), asset_desc varchar(100), source_table_id varchar(64), db_id varchar(64), updated_time timestamp)");
        jdbc.update("insert into db_datasource_t values ('s1','a',0,'A'),('s2','a',0,'B'),('s3','b',0,'C')");
        for (int i = 1; i <= 20; i++) jdbc.update("insert into db_table_t values (?, 'a', 's1', 0, ?, ?, '', 'TABLE', 1, null)", "t" + i, String.format("table_%02d", i), "表" + i);
        jdbc.update("insert into db_table_t values ('view','a','s2',0,'view_1','视图一','','VIEW',2,null)");
        jdbc.update("insert into db_table_t values ('hidden','b','s3',0,'hidden','隐私表','','TABLE',1,null)");
        jdbc.update("insert into db_table_column_t values ('c1','a','t1',0,'id','标识','int','int',11,0,1,1,1,1)");
        jdbc.update("insert into db_table_column_t values ('c2','b','t1',0,'secret','隐私','int','int',11,1,0,0,0,2)");
        jdbc.update("insert into da_catalog_t values ('cat1','a',0,'业务目录','biz','','t1','s1',null)");
        jdbc.update("insert into da_catalog_t values ('cat2','b',0,'隐私目录','secret','','t1','s1',null)");
        service = new MetadataGovernanceService(jdbc);
    }

    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void filtersAndPagesInSqlWithinTenantAndReadsOnlyOwnedColumns() {
        TenantContext.call("a", () -> {
            Map<String,Object> first = service.catalogTablesPage(List.of("s1"), "数据表", "", 1, 15);
            Map<String,Object> second = service.catalogTablesPage(List.of("s1"), "数据表", "", 2, 15);
            assertThat(first.get("total")).isEqualTo(20L);
            assertThat((List<?>) first.get("rows")).hasSize(15);
            assertThat((List<?>) second.get("rows")).hasSize(5);
            assertThat((List<?>) first.get("catalogs")).hasSize(1);
            assertThat(service.catalogTablesPage(List.of("s2"), "视图", "视图", 1, 15).get("total")).isEqualTo(1L);
            assertThat(service.catalogTablesPage(List.of(), null, "", 1, 15).get("total")).isEqualTo(0);
            assertThat(service.catalogTablesPage(null, null, "隐私", 1, 15).get("total")).isEqualTo(0L);
            assertThat(service.catalogTableColumns("t1")).hasSize(1);
            assertThat(service.catalogTableColumns("hidden")).isEmpty();
            return null;
        });
    }
}
