package com.linewell.dataelement.feature.metadatagovernance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
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
    private DataScopeAuthorizationService scopes;

    @BeforeEach
    void setUp() {
        jdbc = spy(new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:metadata_catalog_page;DB_CLOSE_DELAY=-1", "sa", "")));
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
        jdbc.execute("alter table db_datasource_t add created_by varchar(64) default 'owner'");
        jdbc.execute("alter table db_datasource_t add org_id varchar(64) default 'org1'");
        jdbc.execute("alter table db_table_t add record_count bigint default 0");
        jdbc.execute("alter table db_table_t add created_time timestamp");
        jdbc.execute("alter table db_table_column_t add updated_time timestamp");
        scopes = mock(DataScopeAuthorizationService.class);
        scope(DataScopeAuthorizationService.Scope.ALL,"owner",List.of());
        service = new MetadataGovernanceService(jdbc,new ObjectMapper(),null,scopes);
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

    private void scope(DataScopeAuthorizationService.Scope value,String user,List<String> orgs) {
        when(scopes.currentScope(anyString())).thenReturn(new DataScopeAuthorizationService.ScopeDecision("DATA_SOURCE",value,user,orgs,List.of(),true));
    }

    @Test void sortsBeforePaginationWithConstantQueryCountAndRejectsInjectedOrder() {
        for (int i=21;i<=100;i++) jdbc.update("insert into db_table_t (tid,tenant_id,datasource_id,is_del,table_name,table_type,field_count) values (?,'a','s1',0,?,'TABLE',?)","t"+i,"table_"+i,i);
        TenantContext.call("a",()->{
            for(int size:List.of(20,100)) {
                clearInvocations(jdbc);
                var result=service.catalogTablesPage(List.of("s1"),null,"",1,size,null,"fieldCount","desc");
                var rows=(List<Map<String,Object>>)result.get("rows");
                assertThat(rows).hasSize(size);
                assertThat(rows.getFirst().get("field_count")).isEqualTo(100);
                verify(jdbc,times(2)).queryForList(anyString(),any(Object[].class));
                verify(jdbc,times(1)).queryForObject(anyString(),eq(Long.class),any(Object[].class));
            }
            assertThatThrownBy(()->service.catalogTablesPage(null,null,"",1,20,null,"t.tid;drop table db_table_t","asc")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->service.catalogTablesPage(null,null,"",1,20,null,"fieldCount","asc;--")).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test void everyRequestedIdKeepsOwnerAndOrganizationScope() {
        TenantContext.call("a",()->{
            jdbc.update("update db_datasource_t set created_by='someone',org_id='org2' where tid='s2'");
            scope(DataScopeAuthorizationService.Scope.OWNER,"owner",List.of());
            assertThat(service.catalogTablesPage(List.of("s1","s2","s3"),null,"",1,100,null,"fieldCount","desc").get("total")).isEqualTo(20L);
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of("org2"));
            assertThat(service.catalogTablesPage(List.of("s1","s2","s3"),null,"",1,100,null,"tableName","asc").get("total")).isEqualTo(1L);
            assertThat(service.catalogTableColumns("t1","column_name","desc")).isEmpty();
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of());
            assertThat(service.catalogTablesPage(null,null,"",1,100).get("total")).isEqualTo(0L);
            return null;
        });
    }

    @Test void fieldsPageAndBatchStayBoundedAndDoNotReadPerParent() {
        for(int i=21;i<=100;i++)jdbc.update("insert into db_table_t (tid,tenant_id,datasource_id,is_del,table_name) values (?,'a','s1',0,?)","t"+i,"table_"+i);
        for(int i=1;i<=120;i++)jdbc.update("insert into db_table_column_t (tid,tenant_id,table_id,is_del,column_name,ordinal_position,length) values (?,'a','t1',0,?,?,?)","field-"+i,String.format("field_%03d",i),i,i);
        TenantContext.call("a",()->{
            var small=service.catalogColumnsPage("t1",1,20,"length","desc");
            var large=service.catalogColumnsPage("t1",1,100,"length","desc");
            assertThat(small.get("list")).isEqualTo(((List<?>)large.get("list")).subList(0,20));
            assertThat(large.get("total")).isEqualTo(121L);
            for(int size:List.of(20,100)){
                var ids=java.util.stream.IntStream.rangeClosed(1,size).mapToObj(i->"t"+i).toList();clearInvocations(jdbc);
                var result=service.catalogColumnsBatch(ids);assertThat(result).hasSize(size);
                assertThat(result.get("t1")).hasSize(121);
                verify(jdbc,times(2)).queryForList(anyString(),any(Object[].class));
            }
            assertThatThrownBy(()->service.catalogColumnsBatch(List.of("t1","hidden"))).isInstanceOf(IllegalArgumentException.class);
            assertThat(service.catalogColumnsBatch(List.of("t1","t1"))).hasSize(1);
            assertThatThrownBy(()->service.catalogColumnsBatch(java.util.Collections.nCopies(101,"t1"))).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test void associatedApisSortCountsBeforePagingAndKeepThreeReadsForTwentyAndOneHundred() {
        jdbc.execute("alter table da_catalog_t add created_by varchar(64) default 'owner'");
        jdbc.execute("alter table da_catalog_t add org_id varchar(64) default 'org1'");
        jdbc.execute("create table api_info_t(tid varchar(64),tenant_id varchar(64),catalog_id varchar(64),service_name varchar(64),created_by varchar(64),org_id varchar(64),updated_time timestamp,is_del int)");
        jdbc.execute("create table api_param_t(api_id varchar(64),tenant_id varchar(64),is_del int)");
        for(int i=0;i<120;i++)jdbc.update("insert into api_info_t values(?,'a','cat1',?,'owner','org1',NULL,0)","api-"+i,String.format("API %03d",i));
        jdbc.update("insert into api_info_t values('secret','b','cat1','私密','other','org1',NULL,0),('unrelated','a','other','无关','owner','org1',NULL,0),('outside','a','cat1','无权','other','org2',NULL,0)");
        jdbc.update("insert into api_param_t values('api-100','a',0),('api-100','a',0),('api-100','b',0),('api-1','a',0)");
        TenantContext.call("a",()->{
            scope(DataScopeAuthorizationService.Scope.OWNER,"owner",List.of());
            java.util.List<?> small=null;
            for(int size:List.of(20,100)){
                clearInvocations(jdbc);var result=service.catalogServices("cat1",1,size,"count","desc");
                assertThat(result.get("total")).isEqualTo(120L);
                var list=(java.util.List<Map<String,Object>>)result.get("list");
                assertThat(list).hasSize(size);assertThat(list.getFirst().get("id")).isEqualTo("api-100");assertThat(list.getFirst().get("count")).isEqualTo(2L);
                if(small==null)small=list;else assertThat(list.subList(0,20)).isEqualTo(small);
                verify(jdbc,times(2)).queryForList(anyString(),any(Object[].class));
                verify(jdbc,times(1)).queryForObject(anyString(),eq(Long.class),any(Object[].class));
            }
            assertThatThrownBy(()->service.catalogServices("cat2",1,20,null,null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->service.catalogServices("cat1",1,20,"sql injection","asc")).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test void associatedResourceUsesOneAuthorityAndChecksTheCatalogAndTargetSource() {
        jdbc.execute("alter table da_catalog_t add target_table_id varchar(64)");
        jdbc.execute("alter table da_catalog_t add created_by varchar(64) default 'owner'");
        jdbc.execute("alter table da_catalog_t add org_id varchar(64) default 'org1'");
        jdbc.execute("alter table db_datasource_t add db_type varchar(32) default 'mysql'");
        jdbc.execute("alter table db_datasource_t add app_id varchar(64)");
        jdbc.execute("create table sym_application_t(tid varchar(64),tenant_id varchar(64),app_name varchar(64),is_del int)");
        jdbc.update("update da_catalog_t set target_table_id='t1' where tid='cat1'");
        TenantContext.call("a",()->{
            for(int size:List.of(20,100)){
                clearInvocations(jdbc);var result=service.catalogResources("cat1",1,size,"fieldCount","desc");
                assertThat(result.get("total")).isEqualTo(1);assertThat((List<?>)result.get("list")).hasSize(1);
                verify(jdbc,times(2)).queryForList(anyString(),any(Object[].class));
            }
            assertThatThrownBy(()->service.catalogResources("cat2",1,20,null,null)).isInstanceOf(IllegalArgumentException.class);
            jdbc.update("update da_catalog_t set target_table_id='hidden' where tid='cat1'");
            assertThatThrownBy(()->service.catalogResources("cat1",1,20,null,null)).isInstanceOf(IllegalArgumentException.class);
            jdbc.update("update da_catalog_t set target_table_id='t1' where tid='cat1'");
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of());
            assertThatThrownBy(()->service.catalogResources("cat1",1,20,null,null)).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test void mixedParentBatchCannotExpandOwnerOrOrganizationScope() {
        jdbc.update("update db_datasource_t set created_by='someone',org_id='org2' where tid='s2'");
        TenantContext.call("a",()->{
            scope(DataScopeAuthorizationService.Scope.OWNER,"owner",List.of());
            assertThatThrownBy(()->service.catalogColumnsBatch(List.of("t1","view"))).isInstanceOf(IllegalArgumentException.class);
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of("org2"));
            assertThat(service.catalogColumnsPage("t1",1,100,"column_name","asc").get("total")).isEqualTo(0L);
            assertThatThrownBy(()->service.catalogColumnsBatch(List.of("view","t1"))).isInstanceOf(IllegalArgumentException.class);
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of());
            assertThatThrownBy(()->service.catalogColumnsBatch(List.of("view"))).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }
}
