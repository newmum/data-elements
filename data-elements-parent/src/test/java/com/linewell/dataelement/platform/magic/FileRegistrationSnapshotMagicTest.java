package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import cn.hutool.json.JSONUtil;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class FileRegistrationSnapshotMagicTest {
    public static class Scope {
        String policy="ALL";
        public Map<String,Object> resource(String key){return Map.of("scope",policy,"userId","validation-user","organizationIds",List.of("validation-org"));}
    }
    static class Fixture {
        final TableDetailFieldSaveMagicTest.Fixture base=new TableDetailFieldSaveMagicTest.Fixture(0);
        final Scope scope=new Scope();final List<Map<String,Object>> files=new ArrayList<>();
        Fixture(int count){
            base.jdbc.execute("alter table db_datasource_t add pool_cfg clob");
            base.jdbc.execute("alter table db_datasource_t add org_id varchar(64)");base.jdbc.execute("alter table db_datasource_t add created_by varchar(64)");
            base.jdbc.update("update db_datasource_t set pool_cfg=?,org_id='validation-org',created_by='validation-user'","{\"accessMode\":\"receive\",\"pushDataType\":\"UNSTRUCTURED\"}");
            base.jdbc.execute("alter table db_table_t add table_type varchar(32)");base.jdbc.execute("alter table db_table_t add table_name varchar(128)");base.jdbc.execute("alter table db_table_t add table_name_cn varchar(255)");base.jdbc.execute("alter table db_table_t add table_comment varchar(2000)");base.jdbc.execute("alter table db_table_t add created_time timestamp default current_timestamp");
            base.jdbc.execute("delete from db_table_column_t");base.jdbc.execute("delete from db_table_t");
            for(int i=0;i<count;i++){
                String id="file-"+i,code="scene_"+i;var policy=Map.of("fileCode",code,"fileFormat","pdf","maxFileBytes",1048576,"maxFileSizeMb",1);
                base.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,table_name_cn,table_comment,field_governance_config,is_del) values(?,'police','source','FILE',?,?,?, ?,0)",id,"legacy_"+i,"文件"+i,"validation文件",JSONUtil.toJsonStr(Map.of("fileRegistration",policy)));
                files.add(new LinkedHashMap<>(Map.of("tableId",id,"fileCode",code,"fileName","文件"+i,"fileDescription","validation文件","fileFormat","pdf","maxFileSizeMb",1)));
            }
            base.jdbc.execute("insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,table_name_cn,is_del) values('foreign','other','source','FILE','foreign_file','foreign',0)");
        }
        Object run(Map<String,Object> body) throws Exception {
            String script=CanonicalMagicSources.byId("metadata_file_registration_01").replaceAll("(?m)^import (?!java\\.|cn\\.)[^\\r\\n]*\\r?\\n","");
            base.reads=0;
            return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("body",body,"db",base.db,"tenantRuntime",base.scope,"dataScope",scope)));
        }
    }
    @Test void twentyAndHundredDefinitionsUseThreeReadsAndCannotMixForeignIds() throws Exception {
        for(int count:List.of(20,100)){
            var f=new Fixture(count);
            var result=(Map<?,?>)f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files));
            assertEquals(count,((List<?>)result.get("list")).size());assertEquals(3,f.base.reads);
            f.files.getLast().put("tableId","foreign");assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));assertEquals(3,f.base.reads);
        }
    }
    @Test void orgAndOwnerScopesApplyBeforeAnyFileRead() throws Exception {
        for(String policy:List.of("ORG","OWNER")){
            var f=new Fixture(20);f.scope.policy=policy;
            assertEquals(20,((List<?>)((Map<?,?>)f.run(Map.of("dbId","source"))).get("list")).size());assertEquals(3,f.base.reads);
            f.base.jdbc.execute("update db_datasource_t set org_id='other',created_by='another-user'");
            assertInstanceOf(ExitValue.class,f.run(Map.of("dbId","source")));assertEquals(1,f.base.reads);
        }
    }
    @Test void duplicateOrChangedCodesAndForgedTenantAreRejected() throws Exception {
        var f=new Fixture(2);f.files.getLast().put("fileCode","scene_0");
        assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));
        f.files.getLast().put("fileCode","renamed_code");assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));
        assertInstanceOf(ExitValue.class,f.run(Map.of("dbId","source","tenantId","other")));assertEquals(0,f.base.reads);
    }
    @Test void associatedTableIdsUseTheSameRoleScopeInOneBatch() throws Exception {
        for(int count:List.of(20,100)){
            var f=new Fixture(count);f.scope.policy="OWNER";
            f.base.jdbc.execute("insert into db_datasource_t(tid,tenant_id,is_del,org_id,created_by) values('other-source','police',0,'other','another-user')");
            f.base.jdbc.execute("insert into db_table_t(tid,tenant_id,datasource_id,table_type,table_name,is_del) values('associated','police','source','TABLE','validation_table',0),('hidden','police','other-source','TABLE','hidden_table',0)");
            f.files.forEach(file->file.put("relatedTableName","associated"));
            assertInstanceOf(Map.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));assertEquals(4,f.base.reads);
            f.files.getLast().put("relatedTableName","hidden");assertInstanceOf(ExitValue.class,f.run(Map.of("action","save","dbId","source","dryRun",true,"files",f.files)));assertEquals(4,f.base.reads);
        }
    }
}
