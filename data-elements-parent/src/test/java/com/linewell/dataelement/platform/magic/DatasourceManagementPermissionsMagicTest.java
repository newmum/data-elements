package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Executes canonical management scripts against three distinct ownership groups. */
class DatasourceManagementPermissionsMagicTest {
    @ParameterizedTest
    @CsvSource({"ALL,20", "ALL,100", "ORG,20", "ORG,100", "OWNER,20", "OWNER,100"})
    void cardsAndStatisticsUseTheSameRoleAndKeepQueryCountsConstant(String policy, int size) throws Exception {
        var fixture=new Fixture(policy,size);
        var cards=(Map<?,?>)fixture.run("datapd_dept_datasources_01",Map.of(
                "mode","summary","tenantId","foreign","orgId","dept-b","scope","ALL"));
        assertThat(ids((List<?>)cards.get("list"))).isEqualTo(fixture.allowedIds());
        assertThat(cards.get("dataScope")).isEqualTo(policy);
        assertThat(fixture.counter.reads).isEqualTo(3);
        assertThat(fixture.scope.decisions).isEqualTo(1);
        assertThat(fixture.index.reads).isEqualTo(2);
        var tree=(Map<?,?>)fixture.run("datapd_database_tree_statistics_01",Map.of("tenantId","foreign"));
        assertThat(((Number)tree.get("total")).intValue()).isEqualTo(fixture.allowedIds().size());
        assertThat(fixture.counter.reads).isEqualTo(1);
        assertThat(fixture.scope.decisions).isEqualTo(1);
        assertThat(fixture.index.reads).isEqualTo(1);
        System.out.println(policy+" "+size+" rows/group: cards SQL=3 ES=2 scope=1; tree SQL=1 ES=1 scope=1");
    }

    @ParameterizedTest
    @CsvSource({"ALL,20", "ALL,100", "ORG,20", "ORG,100", "OWNER,20", "OWNER,100"})
    void paginationAndGlobalSummaryApplyPermissionsBeforeLimit(String policy,int size) throws Exception {
        var fixture=new Fixture(policy,size);
        var response=(Map<?,?>)fixture.run("datapd_database_page_01",Map.of("pageSize",size,"pageNum",1,
                "orgId","dept-b","createdBy","person-b","scope","ALL"));
        assertThat(((Number)response.get("total")).intValue()).isEqualTo(fixture.allowedIds().size());
        assertThat(ids((List<?>)response.get("list"))).isSubsetOf(fixture.allowedIds()).hasSize(size);
        assertThat(((Number)((Map<?,?>)response.get("summary")).get("sourceCount")).intValue())
                .isEqualTo(fixture.allowedIds().size());
        assertThat(fixture.counter.reads).isEqualTo(4);
        assertThat(fixture.scope.decisions).isEqualTo(1);
    }

    @Test void dropdownAndDetailDoNotTrustForgedEsOwnership() throws Exception {
        var fixture=new Fixture("OWNER",20);
        var result=(List<?>)fixture.run("datapd_database_list_01",Map.of());
        assertThat(ids(result)).isEqualTo(fixture.allowedIds());
        assertThat(fixture.counter.reads).isEqualTo(1);
        assertThat(fixture.scope.decisions).isEqualTo(1);
        reject(fixture.run("datapd_database_detail_01",Map.of("tid","dept-0","silent",true)),403);
        assertThat(fixture.counter.reads).isEqualTo(1);
        reject(fixture.run("datapd_database_detail_01",Map.of("tid","foreign")),404);
    }

    @Test void unauthorizedUpdatesCompletionAndDeletesStopBeforeSideEffects() throws Exception {
        for(String policy:List.of("ORG","OWNER")) {
            var fixture=new Fixture(policy,20);
            reject(fixture.run("datapd_save_update_datasource_01",Map.of("tid","other-0","propList",Map.of("dbName","forged"))),403);
            assertThat(fixture.counter.writes).isZero();
            reject(fixture.run("datapd_save_update_datasource_01",Map.of("tid","other-0","propList",Map.of("completeOnly",true,"dryRun",true))),403);
            assertThat(fixture.counter.writes).isZero();
            reject(fixture.run("datapd_database_delete_01",Map.of("tid","other-0","dryRun",true)),403);
            assertThat(fixture.counter.reads).isEqualTo(2);
            assertThat(fixture.counter.writes).isZero();
            assertThat(fixture.index.reads).isZero();
            reject(fixture.run("datapd_database_delete_01",Map.of("tid","missing","dryRun",true)),404);
            assertThat(fixture.counter.reads).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @CsvSource({"20","100"})
    void batchAndSingleSourceTableReadsRejectEveryUnauthorizedId(int size) throws Exception {
        var fixture=new Fixture("OWNER",size);
        reject(fixture.run("datapd_dept_datasources_01",Map.of("mode","tables","datasourceId","dept-0")),404);
        assertThat(fixture.counter.reads).isEqualTo(1);
        for(String denied:List.of("dept-0","foreign","deleted","missing")) {
            var requested=new ArrayList<String>();
            for(int i=0;i<size-1;i++)requested.add("own-"+i);
            requested.add(denied);
            reject(fixture.run("datapd_dept_datasources_01",Map.of("mode","reportTables","datasourceIds",requested)),403);
            assertThat(fixture.counter.reads).isEqualTo(1);
            assertThat(fixture.scope.decisions).isEqualTo(1);
            assertThat(fixture.counter.writes).isZero();
        }
        fixture.scope.organizations=List.of();fixture.scope.policy="ORG";
        var empty=(Map<?,?>)fixture.run("datapd_dept_datasources_01",Map.of("mode","summary","orgId","dept-a"));
        assertThat((List<?>)empty.get("list")).isEmpty();
    }

    private static void reject(Object response,int code) {
        assertThat(response).isInstanceOf(ExitValue.class);
        assertThat(((ExitValue)response).getValues()[0].toString()).isEqualTo(Integer.toString(code));
    }
    private static Set<String> ids(List<?> rows) {
        var result=new TreeSet<String>();
        for(Object row:rows)result.add(String.valueOf(((Map<?,?>)row).get("tid")));
        return result;
    }

    static class Fixture {
        final JdbcTemplate jdbc;final SQLModule db;final Counter counter=new Counter();
        final Scope scope;final Index index;final int size;
        Fixture(String policy,int size) {
            this.size=size;scope=new Scope(policy);
            var source=new DriverManagerDataSource("jdbc:h2:mem:permission_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc=new JdbcTemplate(source);
            jdbc.execute("""
                create table db_datasource_t(tid varchar(64),tenant_id varchar(64),is_del int,
                    db_name varchar(100),db_type varchar(32),database_type varchar(32),remark varchar(100),
                    app_id varchar(64),org_id varchar(64),created_by varchar(64),pool_cfg varchar(4000),
                    asset_status int,flow_status int,table_num int,data_catalog_num int,is_enable int,show_connect int,
                    connection_status varchar(32),storage_domain varchar(32),node_id varchar(64),db_version varchar(32),
                    created_time timestamp,updated_time timestamp,reg_time timestamp)
                """);
            jdbc.execute("create table sym_application_t(tid varchar(64),tenant_id varchar(64),app_name varchar(100),is_del int)");
            jdbc.execute("create table rm_org_t(id varchar(64),name varchar(100),org_path varchar(100),deleted int)");
            jdbc.execute("""
                create table db_table_t(tid varchar(64),tenant_id varchar(64),datasource_id varchar(64),is_del int,
                    asset_status int,annotated int,business_type varchar(32),field_count int,
                    table_name varchar(64),table_name_en varchar(64),table_name_cn varchar(64),table_comment varchar(100),
                    table_type varchar(32),record_count int,timestamp_field varchar(64),created_time timestamp,updated_time timestamp)
                """);
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(64),table_id varchar(64),is_del int)");
            jdbc.execute("create table metadata_table_collection_job_t(tenant_id varchar(64),datasource_id varchar(64),is_del int,status varchar(32),finished_time timestamp)");
            for(int i=0;i<size;i++) {
                insert("own-"+i,"ga","dept-a","person-a",0);
                insert("dept-"+i,"ga","dept-a","person-b",0);
                insert("other-"+i,"ga","dept-b","person-b",0);
            }
            insert("foreign","foreign","dept-a","person-a",0);insert("deleted","ga","dept-a","person-a",1);
            var dynamic=new MagicDynamicDataSource();dynamic.setDefault(source);db=new SQLModule(dynamic);db.setDataSourceNode(dynamic.getDataSource());
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("camel");
            db.setColumnMapperProvider(columns);db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(v->v);
            db.setSqlInterceptors(List.of(counter));db.setNamedTableInterceptors(List.of());
            index=new Index(jdbc.queryForList("select tid from db_datasource_t"));
        }
        void insert(String id,String tenant,String org,String creator,int deleted) {
            jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,db_name,db_type,database_type,app_id,org_id,created_by,pool_cfg,asset_status,table_num,updated_time) values(?,?,?,?,'mysql','mysql','app',?,?,'{}',2,0,CURRENT_TIMESTAMP)",id,tenant,deleted,id,org,creator);
        }
        Set<String> allowedIds() {
            var ids=new TreeSet<String>();
            for(int i=0;i<size;i++) {
                ids.add("own-"+i);
                if(!scope.policy.equals("OWNER"))ids.add("dept-"+i);
                if(scope.policy.equals("ALL"))ids.add("other-"+i);
            }
            return ids;
        }
        Object run(String id,Map<String,Object> body) throws Exception {
            counter.reads=0;counter.writes=0;scope.decisions=0;index.reads=0;
            String script=CanonicalMagicSources.byId(id).replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n","");
            script=script.replaceAll("(?s)coalesce\\(\\s*nullif\\(json_unquote.*?\\) as accessMode","'upload' as accessMode")
                    .replaceAll("(?s)upper\\(coalesce\\(nullif\\(json_unquote.*?\\) as pushDataType","'STRUCTURED' as pushDataType")
                    .replaceAll("(?s)coalesce\\(json_length\\(json_extract.*?\\) as reportFileCount","0 as reportFileCount");
            script="var me=()=>{orgId:'dept-a',orgName:'本部门'};var configData=()=>{es_dataassets:'assets'};\n"+script;
            var variables=new LinkedHashMap<String,Object>();variables.put("body",body);variables.put("db",db);
            variables.put("dataScope",scope);variables.put("tenantRuntime",scope);variables.put("tenantAccounts",scope);
            variables.put("tenantEs",index);variables.put("tableSort",new TableSortMagicModule());
            return MagicScript.create(script,null).execute(new MagicScriptContext(variables));
        }
    }
    public static class Scope {
        String policy;List<String> organizations=List.of("dept-a");int decisions;
        Scope(String policy){this.policy=policy;}
        public String id(){return "ga";}
        public Map<String,String> names(List<?> ids){return Map.of();}
        public Map<String,Object> resource(String resource){decisions++;return Map.of("scope",policy,"userId","person-a","organizationIds",organizations);}
        public List<Map<String,Object>> visibleDataSourcesFor(String resource,List<Map<String,Object>> rows){decisions++;return rows.stream().filter(this::allowed).toList();}
        public boolean canReadDataSourceFor(String resource,Map<String,Object> row){decisions++;return allowed(row);}
        private boolean allowed(Map<String,Object> row){return policy.equals("ALL")||policy.equals("ORG")&&organizations.contains(Objects.toString(row.get("orgId"),""))||policy.equals("OWNER")&&"person-a".equals(row.get("createdBy"));}
    }
    public static class Index {
        int reads;final List<Map<String,Object>> rows;
        Index(List<Map<String,Object>> rows){this.rows=rows;}
        public Index index(String name){return this;}
        public Map<String,Object> search(Map<?,?> query){reads++;return Map.of("hits",Map.of("hits",rows.stream().map(row->Map.of("_id",row.get("tid"),"_source",new LinkedHashMap<>(Map.of("tid",row.get("tid"),"orgId","dept-a","createdBy","person-a","dbName",row.get("tid"),"appId","app","appName","系统","assetType","db")))).toList()));}
    }
    static class Counter implements SQLInterceptor {
        int reads,writes;
        @Override public void preHandle(BoundSql sql,RequestEntity request){if(sql.getSql().stripLeading().toLowerCase().startsWith("select"))reads++;else writes++;}
    }
}
