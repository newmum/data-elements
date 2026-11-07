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

class RegisteredGovernanceColumnBatchMagicTest {
    public static class MetadataRecorder {
        final List<Map<String,Object>> inserted = new ArrayList<>();
        public int batchInsertTableColumns(List<Map<String,Object>> rows) {
            inserted.addAll(rows);
            return rows.size();
        }
    }

    @Test
    void registrationSavesNullableEditsAndPreservesUnknownWithOneRead() throws Exception {
        for (int count : List.of(20, 100)) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:governance_nullable_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(64),table_id varchar(64),column_name varchar(64),column_comment varchar(128),data_type varchar(64),column_type varchar(64),length bigint,precision_length int,scale int,nullable int,default_value varchar(64),primary_key int,auto_increment int,is_unique int,indexed int,ordinal_position int,charset varchar(64),collation varchar(64),extra varchar(64),source_table_column_id varchar(64),is_del int,created_time timestamp,updated_time timestamp)");
            var items = new ArrayList<Map<String,Object>>();
            for (int i = 0; i < count - 2; i++) {
                Integer original = i == 2 ? null : (i % 2 == 0 ? 1 : 0);
                jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,nullable,is_del) values(?,'police','source',?,?,0)", "column-" + i, "field_" + i, original);
                var item = new LinkedHashMap<String,Object>();
                item.put("columnName", "field_" + i);
                if (i == 2) item.put("nullable", null);
                else if (i != 3) item.put("nullable", i % 2);
                items.add(item);
            }
            var unknownNew = new LinkedHashMap<String,Object>();
            unknownNew.put("columnName", "new_unknown"); unknownNew.put("nullable", null);
            items.add(unknownNew);
            items.add(Map.of("columnName", "new_legacy"));
            jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,nullable,is_del) values('foreign-tenant','other','source','field_0',1,0),('foreign-table','police','other','field_0',1,0)");
            var sources = new MagicDynamicDataSource(); sources.setDefault(source); var db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource()); db.setResultProvider(new DefaultResultProvider("governance-nullable-test"));
            var dialects = new DialectAdapter(); dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}}); db.setDialectAdapter(dialects);
            var columns = new ColumnMapperAdapter(); columns.add(new DefaultColumnMapperProvider()); columns.add(new CamelColumnMapperProvider()); columns.setDefault("camel"); db.setColumnMapperProvider(columns); db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper()); db.setRowMapColumnMapper(value -> value); db.setNamedTableInterceptors(List.of());
            int[] reads = {0}; db.setSqlInterceptors(List.of(new SQLInterceptor(){@Override public void preHandle(BoundSql sql, RequestEntity request){if(sql.getSql().stripLeading().toLowerCase().startsWith("select")) reads[0]++;}}));
            String canonical = CanonicalMagicSources.byId("datapd_db_table_govern_01");
            MagicScript.create(canonical, null).compile();
            String helpers = canonical.substring(canonical.indexOf("var textOf ="), canonical.indexOf("// 解析请求体并为可选参数设置默认值。"));
            var script = "import java.util.Map\nimport cn.hutool.core.util.IdUtil\nvar tenantId='police'\n" + helpers + "\nreturn saveColumns('source',items)";
            var metadata = new MetadataRecorder();
            MagicScript.create(script, null).execute(new MagicScriptContext(Map.of("db", db, "items", items, "metadataAsset", metadata)));
            assertEquals(1, reads[0], count + " fields must use one scoped prefetch");
            assertEquals(0, jdbc.queryForObject("select nullable from db_table_column_t where tid='column-0'", Integer.class));
            assertEquals(1, jdbc.queryForObject("select nullable from db_table_column_t where tid='column-1'", Integer.class));
            assertNull(jdbc.queryForObject("select nullable from db_table_column_t where tid='column-2'", Integer.class));
            assertEquals(0, jdbc.queryForObject("select nullable from db_table_column_t where tid='column-3'", Integer.class));
            assertEquals(1, jdbc.queryForObject("select nullable from db_table_column_t where tid='foreign-tenant'", Integer.class));
            assertEquals(1, jdbc.queryForObject("select nullable from db_table_column_t where tid='foreign-table'", Integer.class));
            assertEquals(2, metadata.inserted.size());
            assertNull(metadata.inserted.stream().filter(row -> row.get("column_name").equals("new_unknown")).findFirst().orElseThrow().get("nullable"));
            assertEquals(1, metadata.inserted.stream().filter(row -> row.get("column_name").equals("new_legacy")).findFirst().orElseThrow().get("nullable"));
            System.out.println("Registration nullable: fields=" + count + ", SQL reads=" + reads[0] + ", edits and unknown retained, foreign records unchanged");
        }
    }

    @Test
    void twentyAndHundredFieldsUseOneScopedReadAndRejectForeignColumnIds() throws Exception {
        for (int count : List.of(20,100)) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:governance_columns_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(64),table_id varchar(64),column_name varchar(64),is_del int)");
            var items = new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++) {
                jdbc.update("insert into db_table_column_t values(?,'police','source',?,0)","column-"+i,"field_"+i);
                items.add(i%2==0 ? Map.of("tid","column-"+i) : Map.of("columnName","field_"+i));
            }
            jdbc.update("insert into db_table_column_t values('foreign-tenant','other','source','secret',0)");
            jdbc.update("insert into db_table_column_t values('foreign-table','police','other','other_field',0)");
            jdbc.update("insert into db_table_column_t values('deleted','police','source','deleted_field',1)");
            items.addAll(List.of(Map.of("tid","foreign-tenant"),Map.of("tid","foreign-table"),Map.of("tid","deleted")));
            var sources=new MagicDynamicDataSource();sources.setDefault(source);var db=new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());db.setResultProvider(new DefaultResultProvider("governance-test"));
            var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});db.setDialectAdapter(dialects);
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("camel");db.setColumnMapperProvider(columns);db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(value->value);db.setNamedTableInterceptors(List.of());
            int[] reads={0};db.setSqlInterceptors(List.of(new SQLInterceptor(){@Override public void preHandle(BoundSql sql,RequestEntity request){if(sql.getSql().stripLeading().toLowerCase().startsWith("select"))reads[0]++;}}));
            String canonical=CanonicalMagicSources.byId("datapd_db_table_govern_01");
            String helpers=canonical.substring(canonical.indexOf("var textOf ="),canonical.indexOf("// 解析请求体并为可选参数设置默认值。"));
            var variables=new LinkedHashMap<String,Object>();variables.put("db",db);variables.put("items",items);
            var result=MagicScript.create("import java.util.Map\nvar tenantId='police'\n"+helpers+"\nreturn registeredColumnItems('source',items)",null)
                    .execute(new MagicScriptContext(variables));
            var resolved=assertInstanceOf(List.class,result);assertEquals(count,resolved.size());assertEquals(1,reads[0]);
            for(Object value:resolved){var row=assertInstanceOf(Map.class,assertInstanceOf(Map.class,value).get("column"));assertEquals("police",row.get("tenantId"));assertEquals("source",row.get("tableId"));}
            System.out.println("Governance field matching: fields="+count+", SQL reads="+reads[0]+", foreign/deleted IDs excluded");
        }
    }
}
