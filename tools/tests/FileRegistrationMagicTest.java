import java.nio.file.*;
import java.util.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Executes canonical file registration with the real Magic SQL module and H2; no live data changes. */
public class FileRegistrationMagicTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static String script() throws Exception {
        String source=Files.readString(Path.of("magic/api/02.数据盘点/02.数据库表/01.元数据管理/25.文件登记快照.ms"));
        return source.substring(source.indexOf("================================")+32)
                .replaceAll("(?m)^import (tenantRuntime|log)\\r?\\n","")
                .replaceAll("(?m)^import '@post:/dst/maintenance/refresh' as refreshAssetIndex\\r?\\n","")
                .replace("refreshAssetIndex()","text(sourceId)");
    }
    static PushRegistrySyncMagicTest.Fixture fixture() throws Exception {
        var f=new PushRegistrySyncMagicTest.Fixture(0);
        for(String column:List.of("flow_status int","updated_time timestamp"))f.jdbc.execute("alter table db_datasource_t add "+column);
        for(String column:List.of("table_type varchar(64)","business_type varchar(64)","annotated int","field_count int","org_id varchar(64)","flow_status int","created_time timestamp","updated_time timestamp","reg_time timestamp"))f.jdbc.execute("alter table db_table_t add if not exists "+column);
        f.jdbc.execute("alter table db_table_t alter column table_comment varchar(3000)");
        for(String column:List.of("length bigint","created_time timestamp","updated_time timestamp"))f.jdbc.execute("alter table db_table_column_t add "+column);
        f.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'","{\"accessMode\":\"receive\",\"pushDataType\":\"UNSTRUCTURED\"}");
        f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_type,is_del) values('linked','ga','source','person','数据表',0),('foreign-file','ct','foreign','foreign_file','FILE',0),('foreign-table','ct','foreign','foreign_table','数据表',0)");
        f.jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,is_del) values('linked-column','ga','linked','photo_id',0),('foreign-column','ct','linked','foreign_secret',0)");
        return f;
    }
    static Object call(PushRegistrySyncMagicTest.Fixture f,Map<String,Object> body) throws Exception {
        f.reads.set(0);f.writes.set(0);
        return MagicScript.create(script(),null).execute(new MagicScriptContext(Map.of("body",body,"db",f.db,"tenantRuntime",f.scope)));
    }
    static Map<String,Object> row(int index){return new LinkedHashMap<>(Map.of("fileName","验证文件"+index,"fileDescription","合成文件登记定义","fileFormat","jpg","maxFileSizeMb",0.1,"relatedTableName","person","relatedFieldName","photo_id"));}
    static void rejected(Object response,int code){check(response instanceof ExitValue,"expected rejection, got "+response);check(((ExitValue)response).getValues()[0].toString().equals(""+code),"wrong rejection code "+response);}
    public static void main(String[] args) throws Exception {
        MagicScript.create(script(),null).compile();
        for(int count:List.of(20,100)){
            var f=fixture();var files=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)files.add(row(i));
            Object saved=call(f,Map.of("dbId","source","action","save","files",files));
            check(saved instanceof Map,"save failed: "+saved);int saveReads=f.reads.get();
            check(saveReads==5,"new file registration uses source+file rows+two association batches+lock: "+saveReads);
            check(f.jdbc.queryForObject("select count(*) from db_table_t where tenant_id='ga' and table_type='FILE'",Integer.class)==count,"reused file table snapshots");
            check(f.jdbc.queryForObject("select count(*) from db_table_column_t where tenant_id='ga' and table_id<>'linked'",Integer.class)==count*6,"six attribute snapshots per file");
            Map<?,?> loaded=(Map<?,?>)call(f,Map.of("dbId","source","action","read"));check(f.reads.get()==3,"read SQL fixed at three");
            var rows=(List<Map<String,Object>>)loaded.get("list");check(rows.size()==count,"all rows");
            check(rows.stream().allMatch(r->((Number)r.get("maxFileSizeMb")).doubleValue()==0.1),"fractional MB survives round trip");
            Object repeated=call(f,Map.of("dbId","source","action","save","files",rows));check(repeated instanceof Map,"repeat failed "+repeated);check(f.reads.get()==6,"existing columns read once");
            check(f.jdbc.queryForObject("select count(*) from db_table_t where tenant_id='ga' and table_type='FILE'",Integer.class)==count,"reimport preserves IDs");
            var mixed=new ArrayList<Map<String,Object>>(rows);var bad=new LinkedHashMap<>(mixed.getLast());bad.put("tableId","foreign-file");mixed.set(mixed.size()-1,bad);
            rejected(call(f,Map.of("dbId","source","action","save","files",mixed)),404);check(f.writes.get()==0,"foreign file ID writes nothing");
            var forbidden=new LinkedHashMap<>(row(500));forbidden.put("relatedTableName","foreign-table");
            rejected(call(f,Map.of("dbId","source","action","save","files",List.of(forbidden))),422);check(f.writes.get()==0,"foreign relation writes nothing");
            forbidden.put("relatedTableName","person");forbidden.put("relatedFieldName","foreign_secret");
            rejected(call(f,Map.of("dbId","source","action","save","files",List.of(forbidden))),422);check(f.writes.get()==0,"foreign field excluded");
            Object complete=call(f,Map.of("dbId","source","action","complete"));check(complete instanceof Map,"complete failed: "+complete);
            check(f.jdbc.queryForObject("select asset_status from db_datasource_t where tid='source'",Integer.class)==2,"source completed without business-table registration");
            check(call(f,Map.of("dbId","source","action","save","files",rows)) instanceof Map,"unchanged saved definition");
            check(f.jdbc.queryForObject("select asset_status from db_datasource_t where tid='source'",Integer.class)==2,"unchanged reimport preserves completion");
            rejected(call(f,Map.of("dbId","foreign","action","read")),404);
            System.out.println(count+" files: new save=5 reads, reimport=6 reads, read=3 reads; snapshot IDs stable; cross-tenant file/table/field references rejected before writes.");
        }
    }
}
