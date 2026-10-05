package com.linewell.dataelement.dataservice.push;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

class DataPushSchemaRegistrationStatusTest {
    private JdbcTemplate jdbc;
    private DataPushSchemaService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource datasource = new JdbcDataSource();
        datasource.setURL("jdbc:h2:mem:push_status_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(datasource);
        // Deliberately omit register_status and all workflow columns: the new runtime must not query them.
        jdbc.execute("create table db_datasource_t(tid varchar(32), tenant_id varchar(32), db_name varchar(255), asset_status int, pool_cfg varchar(1000), is_del int)");
        jdbc.execute("create table db_table_t(tid varchar(32), tenant_id varchar(32), datasource_id varchar(32), table_name varchar(255), table_name_cn varchar(255), table_comment varchar(255), asset_status int, is_del int)");
        TenantDataSourceRegistry tenants = mock(TenantDataSourceRegistry.class);
        when(tenants.dataSourceForTenant("tenant-1")).thenReturn(datasource);
        service = new DataPushSchemaService(tenants, new ObjectMapper(), mock(DataPushFjghccDeliveryService.class));
    }

    @Test
    void completedAssetStatusPassesDatasourceEligibilityWithoutLegacyColumn() {
        insertSource(2);
        assertThatThrownBy(() -> service.publish("tenant-1", "source-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据源尚无登记成功的数据表，无法发布推送契约");
    }

    @Test
    void unfinishedAssetStatusCannotPublishPushContract() {
        insertSource(0);
        assertThatThrownBy(() -> service.publish("tenant-1", "source-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据源尚未完成登记");
    }

    @Test
    void publishesMultipleRegisteredTablesAndBuildsStandaloneSnapshot() {
        insertSource(2);
        jdbc.execute("create table db_table_column_t(tid varchar(32),tenant_id varchar(32),table_id varchar(32),column_name varchar(255),column_comment varchar(255),data_type varchar(64),column_type varchar(64),nullable int,primary_key int,ordinal_position int,is_del int)");
        jdbc.execute("create table data_push_schema_t(tid varchar(64),datasource_id varchar(32),table_id varchar(32),table_name varchar(255),schema_version bigint,schema_hash varchar(128),status varchar(32),delivery_config varchar(1000),tenant_id varchar(32),published_time timestamp,updated_time timestamp,is_del int)");
        jdbc.execute("create table data_push_schema_field_t(tid varchar(64),schema_id varchar(64),field_name varchar(255),data_type varchar(64),required_flag int,ordinal_position int,tenant_id varchar(32),is_del int)");
        jdbc.update("insert into db_table_t values ('table-1','tenant-1','source-1','first','第一表','',2,0),('table-2','tenant-1','source-1','second','第二表','',2,0)");
        jdbc.update("insert into db_table_column_t values ('c1','tenant-1','table-1','id','标识','int','int',0,1,1,0),('c2','tenant-1','table-2','name','名称','varchar','varchar(64)',1,0,1,0)");

        List<Map<String,Object>> published = service.publish("tenant-1", "source-1");
        assertThat(published).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from data_push_schema_t", Integer.class)).isEqualTo(2);
        Map<String,Object> snapshot = service.standaloneSnapshot("tenant-1", "source-1", "client-1");
        assertThat((List<?>)snapshot.get("tables")).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from data_push_schema_field_t", Integer.class)).isEqualTo(2);
    }

    private void insertSource(int status) {
        jdbc.update("insert into db_datasource_t values(?,?,?,?,?,0)", "source-1", "tenant-1", "source", status, "{\"accessMode\":\"receive\"}");
    }
}
