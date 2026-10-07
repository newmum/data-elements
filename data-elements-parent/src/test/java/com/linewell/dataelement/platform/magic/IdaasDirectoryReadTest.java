package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

public class IdaasDirectoryReadTest {
    private static String source(String name) throws Exception {
        String id = switch (name) {
            case "field-values" -> "8cbd7ac406ed58a2be17e3e76cbefad5";
            case "directory" -> "6b934eef4c6756029a274852f9b15424";
            default -> throw new IllegalArgumentException("Unknown current IAM function: " + name);
        };
        return CanonicalMagicSources.byId(id)
                .replaceAll("(?m)^import (db|identityTransport);\\r?\\n", "")
                .replaceAll("(?m)^import '@/[^\\r\\n]+;\\r?\\n", "");
    }

    private static Object fields(DbStub db, String operation, Object values) throws Exception {
        Map<String,Object> args = new LinkedHashMap<>();
        args.put("db", db); args.put("operation", operation); args.put("identityDomain", "workforce");
        args.put("entityType", "ORG"); args.put("objectId", "org-1"); args.put("values", values); args.put("prior", Map.of());
        return MagicScript.create(source("field-values"), null).execute(new MagicScriptContext(args));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bulkReadQueriesDefinitionsOnceAndMasksSensitiveValues() throws Exception {
        DbStub db = new DbStub();
        List<Map<String,Object>> rows = new ArrayList<>();
        for(int i=0;i<113;i++) rows.add(Map.of("id","org-"+i,"values",Map.of("label","plain","secret",Map.of("encrypted","cipher"),"unknown","hidden")));
        Map<String,Object> result = (Map<String,Object>) fields(db,"view-many",rows);
        assertEquals(1,db.queries); assertEquals(113,result.size());
        assertEquals(Map.of("label","plain","secret","[受保护]"), result.get("org-112"));
    }

    @Test
    void nextReadAndWriteReloadDefinitionsAndRequiredValidationRemains() throws Exception {
        DbStub db = new DbStub();
        fields(db,"view-many",List.of(Map.of("id","org-1","values",Map.of("label","before"))));
        db.required=true;
        assertInstanceOf(ExitValue.class,fields(db,"encode",Map.of()));
        assertEquals(2,db.queries);
    }

    @Test
    @SuppressWarnings("unchecked")
    void directoryFiltersScopeAndPaginatesBeforeBulkFieldRead() throws Exception {
        DirectoryFixture fixture = new DirectoryFixture(List.of("org-2", "org-3"));
        Map<String,Object> result = fixture.read(2, 1);
        assertEquals(2, ((Number)result.get("total")).intValue());
        assertEquals(2, fixture.counter.queries);
        assertEquals(1, fixture.collector.calls);
        assertEquals(List.of("org-3"), fixture.collector.ids);
        List<Map<String,Object>> list = (List<Map<String,Object>>)result.get("list");
        assertEquals("org-3", list.getFirst().get("id"));
        assertEquals(Map.of("label", "org-3"), list.getFirst().get("ext"));
    }

    @TestFactory
    List<DynamicTest> twentyAndHundredRowsUseTwoScopedQueriesAndOneBatchFieldRead() {
        return List.of(20, 100).stream().map(size -> DynamicTest.dynamicTest("rows=" + size, () -> {
            var ids = IntStream.range(0, size).mapToObj(i -> "org-" + i).toList();
            DirectoryFixture fixture = new DirectoryFixture(ids);
            Map<String,Object> result = fixture.read(1, size);
            assertEquals(size, ((Number)result.get("total")).intValue());
            assertEquals(2, fixture.counter.queries);
            assertEquals(1, fixture.collector.calls);
            assertEquals(size, fixture.collector.ids.size());
            assertTrue(ids.containsAll(fixture.collector.ids));
            assertFalse(fixture.collector.ids.contains("out-of-scope"));
            assertFalse(fixture.collector.ids.contains("other-domain"));
        })).toList();
    }

    public static String formatDate(java.sql.Timestamp value, String ignoredPattern) {
        return value == null ? "" : value.toInstant().toString();
    }

    private static class DirectoryFixture {
        final SQLModule db;
        final QueryCounter counter = new QueryCounter();
        final Collector collector = new Collector();
        final List<String> allowedIds;

        DirectoryFixture(List<String> allowedIds) {
            this.allowedIds = allowedIds;
            var source = new DriverManagerDataSource("jdbc:h2:mem:directory_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setSqlInterceptors(List.of(counter));
            db.setNamedTableInterceptors(List.of());
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE ALIAS date_format FOR \"com.linewell.dataelement.platform.magic.IdaasDirectoryReadTest.formatDate\"");
            jdbc.execute("""
                    create table iam_org_t(tid varchar(64) primary key, identity_domain varchar(30),
                        name varchar(100), code varchar(40), parent_id varchar(64), sort_no int,
                        leader_subject_id varchar(64), line_code varchar(40), status varchar(20),
                        version int, created_time timestamp, updated_time timestamp, path varchar(200),
                        depth int, ext_json varchar(4000), is_del int)
                    """);
            for (String id : allowedIds) {
                jdbc.update("insert into iam_org_t(tid,identity_domain,name,sort_no,status,version,ext_json,is_del) values(?,?,?,0,'ACTIVE',1,?,0)",
                        id, "workforce", id, "{\"label\":\"" + id + "\"}");
            }
            jdbc.update("insert into iam_org_t(tid,identity_domain,name,sort_no,status,version,is_del) values('out-of-scope','workforce','Outsider',0,'ACTIVE',1,0)");
            jdbc.update("insert into iam_org_t(tid,identity_domain,name,sort_no,status,version,is_del) values('other-domain','public','Other domain',0,'ACTIVE',1,0)");
        }

        @SuppressWarnings("unchecked")
        Map<String,Object> read(int page, int size) throws Exception {
            String prelude = "var directoryScope=(permission,domain)=>({ctx:{userId:'operator'},all:false,orgIds:allowedIds});\n"
                    + "var fieldValues=(operation,entityType,domain,objectId,values,prior)=>collector.read(operation,values);\n";
            Map<String,Object> args = Map.of("db",db,"collector",collector,"allowedIds",allowedIds,
                    "entity","orgs","operation","list","payload",Map.of("domain","workforce","page",page,"size",size));
            return (Map<String,Object>)assertInstanceOf(Map.class,
                    MagicScript.create(prelude+source("directory"),null).execute(new MagicScriptContext(args)));
        }
    }

    private static class QueryCounter implements SQLInterceptor {
        int queries;
        @Override
        public void preHandle(BoundSql sql, RequestEntity request) { queries++; }
    }

    public static class Collector {
        int calls; List<String> ids;
        public Map<String,Object> read(String operation,List<Map<String,Object>> values) {
            assertEquals("view-many",operation); calls++; ids=values.stream().map(row->String.valueOf(row.get("id"))).toList();
            Map<String,Object> result=new LinkedHashMap<>();
            values.forEach(row->result.put(String.valueOf(row.get("id")),row.get("values")));
            return result;
        }
    }

    public static class DbStub {
        int queries; boolean required;
        public DbStub normal(){return this;}
        public List<Map<String,Object>> select(String sql) {
            queries++;
            if(sql.contains("iam_field_definition_t")) return List.of(
                    Map.of("field_key","label","sensitive_flag",0,"required_flag",required?1:0,"value_type","STRING"),
                    Map.of("field_key","secret","sensitive_flag",1,"required_flag",0,"value_type","STRING"));
            List<Map<String,Object>> result=new ArrayList<>();
            for(String id:List.of("org-1","org-2","org-3")) result.add(Map.of("id",id,"identity_domain","workforce","name",id,"status","ACTIVE","version",1,"ext_json","{\"label\":\""+id+"\"}"));
            return result;
        }
    }
}
