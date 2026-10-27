package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.stp.StpUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class ReportRegistrationMagicTest {
    static String source() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.isDirectory(root.resolve("magic"))) root = root.getParent();
        String content = Files.readString(root.resolve("magic/api/02.数据盘点/02.数据库表/01.元数据管理/26.数据上报登记.ms"));
        return content.substring(content.indexOf("================================") + 32);
    }
    public static class Metadata {
        public void refreshRegistrationStatus(String id, boolean finish) { }
    }
    static class Fixture {
        final TableDetailFieldSaveMagicTest.Fixture base;
        Fixture(int count) {
            base = new TableDetailFieldSaveMagicTest.Fixture(count);
            base.jdbc.execute("alter table db_datasource_t add pool_cfg clob");
            base.jdbc.update("update db_datasource_t set pool_cfg=?", "{\"accessMode\":\"upload\"}");
            base.jdbc.execute("alter table db_table_column_t add ordinal_position int default 0");
            base.jdbc.execute("alter table db_table_t add table_name varchar(64) default 'registered_table'");
            base.jdbc.execute("alter table db_table_t add table_name_cn varchar(100) default '登记数据表'");
            base.jdbc.execute("alter table db_table_t add table_comment varchar(100)");
            base.jdbc.execute("alter table db_table_t add record_count bigint default 0");
            base.jdbc.execute("create table data_access_agg_task_t(tenant_id varchar(64),source_table_id varchar(64),target_table_id varchar(64),is_del int)");
            base.jdbc.execute("create table file_upload_t(tid varchar(64),tenant_id varchar(64),created_by varchar(64),is_del int)");
        }
        Object run(Map<String,Object> body) throws Exception {
            String script = source().replaceAll("(?m)^import (?!java\\.|cn\\.hutool\\.|cn\\.dev33\\.|org\\.apache\\.poi\\.|com\\.linewell\\.)[^\\r\\n]*\\r?\\n", "");
            script = "var importSnapshot=()=>{throw new IllegalStateException('existing tables must not be reimported')}\nvar configData=()=>{throw new IllegalStateException('storage must not be reached')}\n" + script;
            base.reads = 0;
            try (var login = mockStatic(StpUtil.class)) {
                login.when(StpUtil::getLoginIdAsString).thenReturn("validation-user");
                return MagicScript.create(script, null).execute(new MagicScriptContext(Map.of("body",body,"db",base.db,"tenantRuntime",base.scope,"metadataAsset",new Metadata(),"jsons",new TableDetailFieldSaveMagicTest.Jsons())));
            }
        }
    }
    @Test void legacyListsUseThreeReadsForTwentyAndHundredFieldsAndExcludeForeignColumns() throws Exception {
        for (int count : List.of(20,100)) {
            Fixture f = new Fixture(count);
            Map<?,?> result = assertInstanceOf(Map.class,f.run(Map.of("action","list","dbId","source","tenantId","other")));
            List<?> tables = (List<?>)result.get("tables");
            assertEquals(1,tables.size());
            assertEquals(count,((List<?>)((Map<?,?>)tables.getFirst()).get("fields")).size());
            assertEquals(3,f.base.reads);
            System.out.println("Report registration list: fields="+count+", SQL=3, foreign table/columns excluded");
        }
    }
    @Test void twentyAndHundredTablesSaveWithFixedReadsAndRejectMixedOwnerIdsBeforeWriting() throws Exception {
        for (int count : List.of(20,100)) {
            Fixture f = new Fixture(1);
            List<Map<String,Object>> tables = new ArrayList<>();
            for (int i=0;i<count;i++) {
                String id="registered-"+i;
                f.base.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values(?,'police','source',?,0)",id,id.replace('-','_'));
                tables.add(new LinkedHashMap<>(Map.of("tid",id,"tableName",id.replace('-','_'),"tableNameCn","登记数据表","recordCount",2,"legacy",true,"fields",List.of(Map.of("columnName","id")))));
            }
            Map<String,Object> body = new LinkedHashMap<>(Map.of("action","save","dbId","source","revision","","files",List.of(),"tables",tables));
            f.base.jdbc.execute("insert into file_upload_t values('registered-file','police','validation-user',0)");
            tables.getFirst().put("legacy",false);
            tables.getFirst().put("fileId","registered-file");
            body.put("files",List.of(Map.of("fileId","registered-file","fileName","validation.csv")));
            tables.getLast().put("tid","foreign");
            assertInstanceOf(ExitValue.class,f.run(body));
            assertEquals("{\"accessMode\":\"upload\"}",f.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'",String.class));
            tables.getLast().put("tid","registered-"+(count-1));
            Map<?,?> saved = assertInstanceOf(Map.class,f.run(body));
            assertEquals(count,((List<?>)saved.get("tables")).size());
            assertEquals(4,f.base.reads);
            System.out.println("Report registration save: tables="+count+", SQL=4 including batch file ownership, mixed tenant IDs rejected before writes");
        }
    }
    @Test void missingSourceForeignFileAndStaleRevisionAreRejected() throws Exception {
        Fixture f = new Fixture(1);
        assertInstanceOf(ExitValue.class,f.run(Map.of("action","list","dbId","missing")));
        f.base.jdbc.execute("insert into file_upload_t values('foreign','other','validation-user',0),('other-user','police','another-user',0)");
        assertInstanceOf(ExitValue.class,f.run(Map.of("action","parse","fileIds",List.of("foreign","other-user"),"tenantId","other")));
        assertEquals(1,f.base.reads);
        assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","revision","stale")));
    }
    @Test void pagedFileHistoryUsesOneReadForTwentyAndHundredFiles() throws Exception {
        for(int count:List.of(20,100)) {
            Fixture f=new Fixture(1);
            var files=new ArrayList<Map<String,Object>>();var tables=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++){files.add(Map.of("fileId","file-"+i,"fileName","data-"+i+".csv"));tables.add(Map.of("fileId","file-"+i,"recordCount",2));}
            f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",Map.of("revision","v1","files",files,"tables",tables))));
            Map<?,?> page=(Map<?,?>)f.run(Map.of("action","history","dbId","source","pageNo",1,"pageSize",20,"sortField","fileName","sortDir","asc"));
            assertEquals(count,((Number)page.get("total")).intValue());assertEquals(20,((List<?>)page.get("list")).size());assertEquals(1,f.base.reads);
            System.out.println("Report file history: files="+count+", SQL=1, tenant constrained, pageSize=20");
        }
    }
    @Test void failedOnlyUploadRemainsVisibleAfterReopenWithoutRestoringLegacyTables() throws Exception {
        Fixture f=new Fixture(1);
        f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",Map.of("revision","saved","tables",List.of(),"files",List.of(Map.of("fileId","empty","fileName","empty.xlsx")),"failedTables",List.of(Map.of("status","failed","error","empty sheet"))))));
        Map<?,?> result=(Map<?,?>)f.run(Map.of("action","list","dbId","source"));
        assertEquals(0,((List<?>)result.get("tables")).size());assertEquals(1,((List<?>)result.get("files")).size());assertEquals(1,((List<?>)result.get("failedTables")).size());assertEquals(1,f.base.reads);
    }
    @Test void parserRecognizesQuotedCsvSeparateRegionsAndRejectsMissingData() throws Exception {
        String script = source();
        String helpers = script.substring(script.indexOf("var positions ="),script.indexOf("if (action == 'parse')"));
        String imports = "import cn.hutool.core.util.IdUtil\nimport java.nio.charset.Charset\nimport java.nio.charset.CodingErrorAction\nimport java.nio.ByteBuffer\nimport java.util.stream.IntStream\nimport java.lang.StringBuilder\nvar text=value=>value==null?'':(value+'').trim()\n";
        List<?> rows = assertInstanceOf(List.class,MagicScript.create(imports+helpers+"return regions(csvRows(payload),'测试表','数据.csv')",null)
                .execute(new MagicScriptContext(Map.of("payload","\uFEFF姓名,说明\r\n张三,\"包含,逗号\"\r\n\r\n编号,状态\nA1,正常\n\n只有表头\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        assertEquals(3,rows.size());
        assertEquals("success",((Map<?,?>)rows.get(0)).get("status"));
        assertEquals(1,((Number)((Map<?,?>)rows.get(0)).get("recordCount")).intValue());
        assertEquals("failed",((Map<?,?>)rows.get(2)).get("status"));
        List<?> collisions = (List<?>)MagicScript.create(imports+helpers+"return regions(csvRows(payload),'测试表','数据.csv')",null)
                .execute(new MagicScriptContext(Map.of("payload","姓名,field_1,FIELD_1\n甲,A,B\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        List<?> fields = (List<?>)((Map<?,?>)collisions.getFirst()).get("fields");
        assertEquals(List.of("field_1","field_1_2","FIELD_1_3"),fields.stream().map(field->((Map<?,?>)field).get("columnName")).toList());
    }
}
