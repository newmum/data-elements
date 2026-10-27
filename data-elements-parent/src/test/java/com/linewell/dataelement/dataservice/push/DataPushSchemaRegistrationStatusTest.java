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
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.jdbc.datasource.DelegatingDataSource;

class DataPushSchemaRegistrationStatusTest {
    private JdbcTemplate jdbc;
    private DataPushSchemaService service;
    private AtomicInteger reads;

    @BeforeEach
    void setUp() {
        JdbcDataSource datasource = new JdbcDataSource();
        datasource.setURL("jdbc:h2:mem:push_status_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(datasource);
        // Deliberately omit register_status and all workflow columns: the new runtime must not query them.
        jdbc.execute("create table db_datasource_t(tid varchar(32), tenant_id varchar(32), db_name varchar(255), asset_status int, pool_cfg varchar(1000), is_del int)");
        jdbc.execute("create table db_table_t(tid varchar(32), tenant_id varchar(32), datasource_id varchar(32), table_name varchar(255), table_name_cn varchar(255), table_comment varchar(255), asset_status int, is_del int, field_governance_config clob, table_type varchar(64))");
        reads = new AtomicInteger();
        var counting = new DelegatingDataSource(datasource) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement") && args[0] instanceof String sql && sql.stripLeading().toLowerCase().startsWith("select")) reads.incrementAndGet();
                    try { return method.invoke(connection, args); }
                    catch (java.lang.reflect.InvocationTargetException exception) { throw exception.getCause(); }
                });
            }
        };
        TenantDataSourceRegistry tenants = mock(TenantDataSourceRegistry.class);
        when(tenants.dataSourceForTenant("tenant-1")).thenReturn(counting);
        service = new DataPushSchemaService(tenants, new ObjectMapper(), mock(DataPushFjghccDeliveryService.class));
    }

    @Test
    void completedAssetStatusPassesDatasourceEligibilityWithoutLegacyColumn() {
        insertSource(2);
        assertThatThrownBy(() -> service.publish("tenant-1", "source-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据源尚无登记成功的数据表，无法发布推送规范");
    }

    @Test
    void unfinishedSourceCanPublishItsSavedTableRegistrations() {
        insertSource(0);
        assertThatThrownBy(() -> service.publish("tenant-1", "source-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据源尚无登记成功的数据表，无法发布推送规范");
    }

    @Test
    void unfinishedEmptySourceCanProvisionAKeyWithoutGrantingAnyTable() {
        insertSource(0);
        Map<String,Object> snapshot = service.standaloneSnapshot("tenant-1", "source-1", "push-source-1");
        assertThat((List<?>)snapshot.get("tables")).isEmpty();
        assertThat(snapshot.get("clientIds")).isEqualTo(List.of("push-source-1"));
        assertThat(jdbc.queryForObject("select asset_status from db_datasource_t", Integer.class)).isZero();
    }

    @Test
    void fileRegistrationSnapshotsDoNotPublishAsStructuredTables() {
        insertSource(2);createPushTables();
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del,table_type) values('file-1','tenant-1','source-1','file_collection','文件','','2',0,'FILE')");
        Map<String,Object> snapshot=service.standaloneSnapshot("tenant-1","source-1","push-source-1");
        assertThat((List<?>)snapshot.get("tables")).isEmpty();
        assertThat(snapshot.get("clientIds")).isEqualTo(List.of("push-source-1"));
        assertThatThrownBy(()->service.publish("tenant-1","source-1")).hasMessage("数据源尚无登记成功的数据表，无法发布推送规范");
    }

    @Test
    void unfinishedSourceStillRequiresOwnershipAndEnabledPushMode() {
        insertSource(0);
        assertThatThrownBy(() -> service.requirePushDatasource("tenant-1", "foreign-source"))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("update db_datasource_t set tenant_id='other'");
        assertThatThrownBy(() -> service.requirePushDatasource("tenant-1", "source-1"))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("update db_datasource_t set tenant_id='tenant-1',pool_cfg='{\"accessMode\":\"direct\"}'");
        assertThatThrownBy(() -> service.requirePushDatasource("tenant-1", "source-1"))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("update db_datasource_t set pool_cfg='{\"accessMode\":\"receive\",\"pushDelivery\":{\"enabled\":false}}'");
        assertThatThrownBy(() -> service.requirePushDatasource("tenant-1", "source-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sourceWithRemainingUnregisteredTablesCanPublishRegisteredTables() {
        insertSource(1);
        assertThatThrownBy(() -> service.publish("tenant-1", "source-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据源尚无登记成功的数据表，无法发布推送规范");
    }

    @Test
    void publishesMultipleRegisteredTablesAndBuildsStandaloneSnapshot() {
        insertSource(0);
        createPushTables();
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values ('table-1','tenant-1','source-1','first','第一表','',2,0),('table-2','tenant-1','source-1','second','第二表','',2,0)");
        jdbc.update("insert into db_table_column_t values ('c1','tenant-1','table-1','id','标识','int','int',0,1,1,0),('c2','tenant-1','table-2','name','名称','varchar','varchar(64)',1,0,1,0)");

        List<Map<String,Object>> published = service.publish("tenant-1", "source-1");
        assertThat(published).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from data_push_schema_t", Integer.class)).isEqualTo(2);
        Map<String,Object> snapshot = service.standaloneSnapshot("tenant-1", "source-1", "client-1");
        assertThat((List<?>)snapshot.get("tables")).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from data_push_schema_field_t", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select asset_status from db_datasource_t", Integer.class)).isZero();
    }

    @Test
    void twentyAndOneHundredSavedTablesUseEightReadsAndExcludeForeignOrUnregisteredRows() {
        for (int count : List.of(20, 100)) {
            setUp();insertSource(0);createPushTables();
            for (int i=0;i<count;i++) {
                jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values (?, 'tenant-1','source-1',?, '', '',2,0)", "t"+i,"table_"+i);
                jdbc.update("insert into db_table_column_t values (?, 'tenant-1',?,'id','','int','int',0,1,1,0)","c"+i,"t"+i);
            }
            jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values ('foreign','other','source-1','foreign','','',2,0),('pending','tenant-1','source-1','pending','','',0,0)");
            jdbc.update("insert into db_table_column_t values ('foreign','other','t0','foreign','','int','int',0,1,2,0)");
            reads.set(0);
            var snapshot=service.standaloneSnapshot("tenant-1","source-1","push-source-1");
            assertThat(reads.get()).isEqualTo(8);
            assertThat((List<?>)snapshot.get("tables")).hasSize(count);
            assertThat(snapshot.toString()).doesNotContain("foreign", "pending");
        }
    }

    @Test
    void businessBatchUsesServerVersionWithoutCallerVersion() {
        insertSource(0);createPushTables();
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values ('t1','tenant-1','source-1','cases','案件','',2,0)");
        jdbc.update("insert into db_table_column_t values ('c1','tenant-1','t1','id','标识','int','int',0,1,1,0)");
        service.publish("tenant-1","source-1");
        jdbc.update("update data_push_schema_t set delivery_config='{\"kafka\":{\"topic\":\"demo-cases\"}}',schema_version=2");
        var result=service.validate("tenant-1","client-1","table-to-kafka",Map.of("datasourceId","source-1","tableName","cases","records",List.of(Map.of("id",1))));
        assertThat(result.get("schemaVersion")).isEqualTo(2L);
    }

    private void createPushTables() {
        jdbc.execute("create table db_table_column_t(tid varchar(32),tenant_id varchar(32),table_id varchar(32),column_name varchar(255),column_comment varchar(255),data_type varchar(64),column_type varchar(64),nullable int,primary_key int,ordinal_position int,is_del int)");
        jdbc.execute("create table data_push_schema_t(tid varchar(64),datasource_id varchar(32),table_id varchar(32),table_name varchar(255),schema_version bigint,schema_hash varchar(128),status varchar(32),delivery_config varchar(1000),tenant_id varchar(32),published_time timestamp,updated_time timestamp,is_del int)");
        jdbc.execute("create table data_push_schema_field_t(tid varchar(64),schema_id varchar(64),field_name varchar(255),data_type varchar(64),required_flag int,ordinal_position int,tenant_id varchar(32),is_del int)");
    }

    @Test
    void attachmentLimitsAreVersionedWithoutLeakingOtherGovernanceOrDeliveryFields() throws Exception {
        insertSource(0);createPushTables();
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values ('t','tenant-1','source-1','cases','','',2,0)");
        jdbc.update("insert into db_table_column_t values ('c','tenant-1','t','file_id','附件编号','varchar','varchar(64)',1,0,1,0)");
        var mapper=new ObjectMapper();
        for (long size : List.of(20971520L,41943040L)) {
            var attachment=Map.of("columnName","file_id","maxFileBytes",size,"allowedExtensions",List.of("jpg","pdf"));
            jdbc.update("update db_table_t set field_governance_config=? where tid='t'", mapper.writeValueAsString(Map.of("attachmentFields",List.of(attachment),"unrelated","must-not-leak")));
            service.publish("tenant-1","source-1");
            var delivery=mapper.readTree(jdbc.queryForObject("select delivery_config from data_push_schema_t",String.class));
            assertThat(delivery.at("/attachmentFields/0/maxFileBytes").asLong()).isEqualTo(size);
            assertThat(delivery.toString()).doesNotContain("must-not-leak");
        }
        assertThat(jdbc.queryForObject("select schema_version from data_push_schema_t",Long.class)).isEqualTo(2L);
    }

    private void insertSource(int status) {
        jdbc.update("insert into db_datasource_t values(?,?,?,?,?,0)", "source-1", "tenant-1", "source", status, "{\"accessMode\":\"receive\"}");
    }
}
