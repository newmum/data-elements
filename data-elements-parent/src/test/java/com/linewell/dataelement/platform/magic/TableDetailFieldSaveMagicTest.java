package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.dialect.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

class TableDetailFieldSaveMagicTest {
    public static class Actor { public String id() { return "validation-user"; } }
    public static class SchemaAdapter {
        int previews;
        List<Map<String,Object>> received;
        public Map<String,Object> preview(Map<String,Object> connection,String table,List<Map<String,Object>> fields,String binding) {
            previews++; received=fields;
            return Map.of("fieldCount",fields.size(),"bindingPresent",binding.startsWith("police:validation-user:"));
        }
    }
    public static class Jsons { public String toJSONStr(Object value) { return cn.hutool.json.JSONUtil.toJsonStr(value); } }
    public static class Scope {
        boolean denied;
        public String id() { return "police"; }
        public List<?> visibleDataSourcesFor(String resource, List<?> rows) { return denied ? List.of() : rows; }
    }
    static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Scope scope = new Scope();
        final List<Map<String,Object>> fields = new ArrayList<>();
        int reads;
        Fixture(int count) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:detail_fields_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(64),is_del int)");
            jdbc.execute("insert into db_datasource_t values('source','police',0)");
            jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(64),datasource_id varchar(64),business_type varchar(64),business_type_reason varchar(64),annotated int,asset_status int,flow_status int,flow_order_id varchar(64),is_del int,field_governance_config clob,timestamp_field varchar(64),dictionary_structure_type varchar(64),dictionary_categories clob,dictionary_profiles clob,dictionary_profile clob,updated_time timestamp)");
            jdbc.execute("insert into db_table_t(tid,tenant_id,datasource_id,business_type,annotated,asset_status,flow_status,flow_order_id,is_del,field_governance_config) values('table','police','source','业务表',0,1,3,'approval',0,'{\"registrationSavedAt\":\"original\",\"attachmentFields\":[{\"columnName\":\"field_0\",\"maxFileBytes\":1024}]}'),('foreign','other','source','业务表',1,2,2,null,0,'{}')");
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(64),table_id varchar(64),column_name varchar(64),column_comment varchar(64),primary_key int,nullable int,data_standard_id varchar(64),enable_code_table int,code_table_id varchar(64),updated_time timestamp,is_del int)");
            for (int i=0;i<count;i++) {
                jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,column_comment,nullable,is_del) values(?,'police','table',?,'原注释',0,0)","c"+i,"field_"+i);
                fields.add(new LinkedHashMap<>(Map.of("tid","c"+i,"columnName","field_"+i,"columnComment","新注释","primaryKey",i==0,"nullable",true,"dataStandardId","DATE","enableCodeTable",0,"codeTableId","")));
            }
            jdbc.execute("insert into db_table_column_t(tid,tenant_id,table_id,column_name,is_del) values('foreign-column','other','table','secret',0)");
            jdbc.execute("insert into db_table_column_t(tid,tenant_id,table_id,column_name,is_del) values('other-table-column','police','other-table','other_field',0)");
            var sources = new MagicDynamicDataSource(); sources.setDefault(source); db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource()); db.setResultProvider(new DefaultResultProvider("detail-fields-test"));
            var dialects = new DialectAdapter(); dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}}); db.setDialectAdapter(dialects);
            var columns=new ColumnMapperAdapter(); columns.add(new DefaultColumnMapperProvider()); columns.add(new CamelColumnMapperProvider()); columns.setDefault("camel"); db.setColumnMapperProvider(columns); db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper()); db.setRowMapColumnMapper(value->value); db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor(){@Override public void preHandle(BoundSql sql,RequestEntity request){if(sql.getSql().stripLeading().toLowerCase().startsWith("select")) reads++;}}));
        }
        Object save(String table, boolean dry) throws Exception {
            String canonical = CanonicalMagicSources.byId("datapd_db_table_govern_01");
            String script = "import java.util.Map\nimport java.util.List\nimport cn.hutool.json.JSONUtil\nimport com.linewell.dataelement.metautil.structured.StructuredSourceProbeService\nvar tenantId=tenantRuntime.id()\n" + canonical.substring(canonical.indexOf("var textOf ="));
            var config = new LinkedHashMap<>(Map.of("fields",fields,"registrationSavedPhase","business","registrationSavedAt","spoofed","attachmentFields",List.of(),"timestampField","field_0"));
            reads=0;
            return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("db",db,"tenantRuntime",scope,"dataScope",scope,"jsons",new Jsons(),"body",Map.of("tableId",table,"editMode","fields","dryRun",dry,"columns",fields,"fieldGovernanceConfig",config))));
        }
        Object schema(String table,SchemaAdapter adapter) throws Exception {
            jdbc.execute("alter table db_table_column_t add column if not exists ordinal_position int default 0");
            jdbc.execute("alter table db_table_t add column if not exists table_name varchar(64) default 'validation_table'");
            String canonical=CanonicalMagicSources.byId("datapd_db_table_govern_01");
            String script="import java.util.Map\nimport java.util.List\nimport cn.hutool.json.JSONUtil\nvar tenantId=tenantRuntime.id()\nvar datasourceConnectionConfig=(a,b,c,d)=>{return {}}\n"+
                    canonical.substring(canonical.indexOf("var textOf =")).replace("StpUtil.getLoginIdAsString()","actor.id()");
            reads=0;
            return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("db",db,"tenantRuntime",scope,"dataScope",scope,"jsons",new Jsons(),"actor",new Actor(),"tableSchemaSync",adapter,
                    "body",Map.of("tableId",table,"schemaAction","compare","columns",List.of(Map.of("tid","foreign-column")),"jdbcURL","forged","sql","DROP TABLE forbidden"))));
        }
    }
    @Test void twentyAndHundredFieldsUseFixedReadsAndPreserveWorkflowAndPhysicalMetadata() throws Exception {
        for (int count : List.of(20,100)) {
            var f=new Fixture(count); assertInstanceOf(Map.class,f.save("table",false)); assertEquals(3,f.reads);
            assertEquals(count,f.jdbc.queryForObject("select count(*) from db_table_column_t where column_comment='新注释'",Integer.class));
            assertEquals(0,f.jdbc.queryForObject("select nullable from db_table_column_t where tid='c0'",Integer.class));
            var table=f.jdbc.queryForMap("select * from db_table_t where tid='table'");
            assertEquals(0,table.get("annotated")); assertEquals(1,table.get("asset_status")); assertEquals(3,table.get("flow_status")); assertEquals("approval",table.get("flow_order_id"));
            String governance=f.jdbc.queryForObject("select field_governance_config from db_table_t where tid='table'",String.class);
            assertTrue(governance.contains("original")); assertTrue(governance.contains("1024")); assertFalse(governance.contains("spoofed")); assertFalse(governance.contains("registrationSavedPhase"));
            System.out.println("Detail field save: fields="+count+", SQL reads="+f.reads+", registration/approval/nullable/attachment policy retained");
        }
    }
    @Test void foreignTenantRoleAndColumnIdsAreRejectedBeforeAnyWrites() throws Exception {
        var f = new Fixture(20);
        assertInstanceOf(ExitValue.class,f.save("foreign",false)); assertEquals(1,f.reads);
        f.scope.denied=true; assertInstanceOf(ExitValue.class,f.save("table",false)); assertEquals(2,f.reads); f.scope.denied=false;
        f.fields.get(19).put("tid","foreign-column"); assertInstanceOf(ExitValue.class,f.save("table",false)); assertEquals(3,f.reads);
        f.fields.get(19).put("tid","other-table-column"); assertInstanceOf(ExitValue.class,f.save("table",false)); assertEquals(3,f.reads);
        assertEquals(0,f.jdbc.queryForObject("select count(*) from db_table_column_t where column_comment='新注释'",Integer.class));
        f.fields.get(19).put("tid","c19"); assertInstanceOf(Map.class,f.save("table",true));
        assertEquals(0,f.jdbc.queryForObject("select count(*) from db_table_column_t where column_comment='新注释'",Integer.class));
    }
    @Test void schemaPreviewLoadsOnlyTenantOwnedSnapshotAndKeepsReadCountConstant() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture(count); var adapter=new SchemaAdapter();
            f.jdbc.update("update db_table_t set field_governance_config=? where tid='table'", "{\"fields\":[{\"columnName\":\"field_0\",\"primaryKey\":true,\"columnComment\":\"已保存注释\"}]}");
            assertInstanceOf(Map.class,f.schema("table",adapter)); assertEquals(4,f.reads); assertEquals(count,adapter.received.size());
            assertTrue(adapter.received.stream().allMatch(row->row.get("tid").toString().matches("c[0-9]+")));
            var first=adapter.received.stream().filter(row->row.get("tid").equals("c0")).findFirst().orElseThrow();
            assertEquals(true,first.get("primaryKey")); assertEquals("已保存注释",first.get("columnComment"));
            f.scope.denied=true; assertInstanceOf(ExitValue.class,f.schema("table",adapter)); assertEquals(2,f.reads); assertEquals(1,adapter.previews);
            f.scope.denied=false; assertInstanceOf(ExitValue.class,f.schema("foreign",adapter)); assertEquals(1,f.reads); assertEquals(1,adapter.previews);
            System.out.println("Schema scope: fields="+count+", fixed 4 business reads plus one connection-config read in runtime, foreign columns excluded before adapter");
        }
    }
}
