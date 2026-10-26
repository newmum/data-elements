import java.nio.file.*;
import java.util.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Exercises the canonical import script against isolated H2 snapshots. */
public class MetadataImportAttachmentMagicTest {
    public static class Jsons { public String toJSONStr(Object value) throws Exception { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value); } }
    public static class Assets { public void refreshRegistrationStatus(String source,boolean value) {} }
    public static class Es { public void saveOrUpdate(String index,String id,String value,boolean refresh) {} }
    public static class Log { public void warn(String message,Object id,Object error) {} }
    static String script;
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static Object run(PushRegistrySyncMagicTest.Fixture f,Map<String,Object> input) {
        f.reads.set(0);f.writes.set(0);
        return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("body",input,"db",f.db,"tenantRuntime",f.scope,"dataScope",f.scope,"jsons",new Jsons(),"metadataAsset",new Assets(),"esCommonService",new Es(),"log",new Log())));
    }
    static PushRegistrySyncMagicTest.Fixture fixture(int count) throws Exception {
        var f=new PushRegistrySyncMagicTest.Fixture(count);
        for(String column:List.of("table_name_en","table_type","business_type","business_type_reason","timestamp_field","field_governance_config","annotated","record_count","field_count","storage_size","total_size_formatted","total_size_bytes","org_id","org_path","data_source_type","source_table_id","source_table_name","manage_unit","asset_desc","flow_status","flow_order_id","updated_time","created_time","reg_time")) {
            if(!column.equals("field_governance_config")) f.jdbc.execute("alter table db_table_t add `"+column+"` varchar(10000)");
        }
        for(String column:List.of("length","precision_length","scale","default_value","auto_increment","is_unique","indexed","data_standard_id","is_dict","dict_name","updated_time","created_time")) f.jdbc.execute("alter table db_table_column_t add `"+column+"` varchar(1000)");
        f.jdbc.execute("alter table db_datasource_t add table_num int");f.jdbc.execute("alter table db_datasource_t add updated_time timestamp");
        return f;
    }
    static Map<String,Object> input(int count) {
        var tables=new ArrayList<Map<String,Object>>();var fields=new LinkedHashMap<String,Object>();
        for(int i=0;i<count;i++) {
            String name="table_"+i;
            tables.add(Map.of("tableName",name,"tableComment","测试业务表","tableType","数据表","businessType","业务表"));
            var column=new LinkedHashMap<String,Object>();column.put("columnName","file_id");column.put("columnComment","附件编号");column.put("columnType","varchar");column.put("columnLength",64);column.put("primaryKey",false);column.put("nullable",true);column.put("isTimestamp",false);
            column.put("attachment",Map.of("enabled",true,"maxFileBytes",20971520,"allowedExtensions",List.of("jpg","pdf")));
            fields.put(name,List.of(column));
        }
        return new LinkedHashMap<>(Map.of("dbId","source","dataSourceType","receive","tables",tables,"fieldMap",fields));
    }
    public static void main(String[] args) throws Exception {
        String source=Files.readString(Path.of("magic/api/02.数据盘点/02.数据库表/01.元数据管理/20.手工导入元数据快照.ms"));
        script=source.substring(source.indexOf("================================")+32).replaceAll("(?m)^import (tenantRuntime|dataScope|metadataAsset|jsons|log)\\r?\\n","").replaceAll("(?m)^import com\\.linewell\\.dataelement\\.elasticsearch\\.EsCommonService as esCommonService\\r?\\n","");
        MagicScript.create(script,null).compile();
        for(int count:List.of(20,100)) {
            var f=fixture(count);var payload=input(count);Object result=run(f,payload);
            check(!(result instanceof ExitValue),"valid import "+result);check(f.reads.get()==4,"fixed 4 reads, got "+f.reads);
            String governance=f.jdbc.queryForObject("select field_governance_config from db_table_t where tid='table_0'",String.class);
            check(governance.contains("attachmentFields")&&governance.contains("20971520"),"attachment constraints persisted");
            check(f.jdbc.queryForObject("select count(*) from db_table_column_t where column_name='file_id' and is_del=0",Integer.class)==count,"all fields saved");
            System.out.println(count+" tables: 4 reads, attachment constraints persisted");
            f.scope.denied=true;payload.put("dryRun",true);check(!(run(f,payload) instanceof ExitValue),"import ignores metadata-menu role scope");check(f.writes.get()==0,"dry preview writes nothing");payload.remove("dryRun");f.scope.denied=false;
            payload.put("dbId","foreign");check(run(f,payload) instanceof ExitValue,"foreign tenant rejected");check(f.writes.get()==0,"tenant rejection writes nothing");
        }
        var f=fixture(1);var payload=input(1);payload.put("dryRun",true);
        ((Map<String,Object>)((List<?>)((Map<?,?>)payload.get("fieldMap")).get("table_0")).getFirst()).put("attachment",Map.of("enabled",true,"maxFileBytes",0,"allowedExtensions",List.of("exe")));
        check(run(f,payload) instanceof ExitValue,"invalid policy rejected");check(f.writes.get()==0,"invalid import has no writes");
        System.out.println("invalid attachment policy and foreign-tenant IDs rejected before writes; role scope absent");
        String governance=Files.readString(Path.of("magic/api/02.数据盘点/02.数据库表/02.数据表管理/05.保存数据表治理配置.ms"));
        governance=governance.substring(governance.indexOf("================================")+32);MagicScript.create(governance,null).compile();
        String preserve="import java.util.Map\nimport java.util.List\nimport cn.hutool.json.JSONUtil\n"+governance.substring(governance.indexOf("var textOf ="),governance.indexOf("var itemValue ="))+governance.substring(governance.indexOf("var previousGovernance ="),governance.indexOf("var dictionaryCategories ="))+"\nreturn governance";
        var policy=List.of(Map.of("columnName","file_id","maxFileBytes",20971520,"allowedExtensions",List.of("pdf")));
        Object kept=MagicScript.create(preserve,null).execute(new MagicScriptContext(Map.of("table",Map.of("fieldGovernanceConfig",new Jsons().toJSONStr(Map.of("attachmentFields",policy))),"governance",new LinkedHashMap<>(Map.of("fields",List.of(),"attachmentFields",List.of())))));
        check(((Map<?,?>)kept).get("attachmentFields").toString().contains("20971520"),"governance keeps template-owned attachment limits");
        Object stripped=MagicScript.create(preserve,null).execute(new MagicScriptContext(Map.of("table",Map.of(),"governance",new LinkedHashMap<>(Map.of("attachmentFields",policy)))));
        check(!((Map<?,?>)stripped).containsKey("attachmentFields"),"governance cannot inject unvalidated attachment limits");
        System.out.println("later governance save preserves template-owned attachment limits");
    }
}
