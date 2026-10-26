import java.nio.file.*;
import java.util.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Canonical registration snapshot read: bounded SQL and tenant isolation. */
public class MetadataTableListMagicTest {
    static String script;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static PushRegistrySyncMagicTest.Fixture fixture(int count) throws Exception {
        var f = new PushRegistrySyncMagicTest.Fixture(0);
        for (String col : List.of("table_name_en","table_type","business_type","business_type_reason",
                "storage_size","total_size_formatted","org_id","org_path","related_directory","data_source_type",
                "timestamp_field","dictionary_structure_type","source_catalog_id","source_table_id","source_table_name",
                "manage_unit","flow_status","reg_time","created_time","updated_time","dictionary_profiles",
                "dictionary_profile","dictionary_categories")) f.jdbc.execute("alter table db_table_t add " + col + " varchar(1000)");
        for (String col : List.of("annotated","record_count","field_count","total_size_bytes","dictionary_table_flag"))
            f.jdbc.execute("alter table db_table_t add " + col + " bigint");
        f.jdbc.execute("create table da_catalog_t(tid varchar(64),tenant_id varchar(32),catalog_name varchar(255),catalog_name_en varchar(255),is_del int)");
        f.jdbc.update("insert into db_datasource_t(tid,tenant_id,db_name,is_del) values('foreign','ct','foreign-db',0)");
        for (int i = 0; i < count; i++) f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,annotated,business_type,is_del,updated_time) values(?,'ga','source',?,1,'业务表',0,'2026-10-26 10:00:00')", "row-"+i, "table_"+i);
        f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,annotated,is_del) values('pending','ga','source','pending',0,0)");
        // Even a malformed historical row reusing a visible source ID must not cross tenant bounds.
        f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,annotated,is_del) values('foreign-row','ct','source','foreign-table',1,0)");
        f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,annotated,is_del) values('foreign-pending','ct','source','foreign-pending',0,0)");
        f.scope.denied = true;
        return f;
    }
    static Object run(PushRegistrySyncMagicTest.Fixture f, Map<String,Object> input) {
        f.reads.set(0); f.writes.set(0);
        var vars = new HashMap<String,Object>(input);
        vars.put("tenantRuntime", f.scope); vars.put("dataScope", f.scope); vars.put("db", f.db);
        return MagicScript.create(script, null).execute(new MagicScriptContext(vars));
    }
    static void rows(Object value, int expected) {
        check(value instanceof List, "expected rows: " + value);
        var list = (List<?>)value;
        check(list.size() == expected, "expected " + expected + " rows, got " + list.size());
        for (Object row : list) check(!((Map<?,?>)row).get("tid").toString().startsWith("foreign"), "foreign row leaked");
    }
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("magic/api/02.数据盘点/02.数据库表/01.元数据管理/06.获取表列表.ms"));
        script = source.substring(source.indexOf("================================") + 32)
                .replaceAll("(?m)^import (tenantRuntime|dataScope|jsons)\\r?\\n", "");
        MagicScript.create(script, null).compile();
        Integer pageReads = null;
        for (int count : List.of(20,100)) {
            var f = fixture(count);
            Object result = run(f,Map.of("dbId","source","paged",true,"pageSize",count,"includeGovernance",false));
            check(result instanceof Map, "registration is not blocked by the metadata menu role: " + result);
            rows(((Map<?,?>)result).get("rows"), count);
            if (pageReads == null) pageReads = f.reads.get();
            check(f.reads.get() == pageReads && pageReads <= 6, "SQL count grows with rows: " + f.reads);
            check(f.writes.get() == 0, "snapshot read wrote business data");
            System.out.println(count + " rows: one page request, " + f.reads + " SQL reads; metadata-menu role check absent");
            rows(run(f,Map.of("dbId","source","includeGovernance",false)),count+1);
            check(f.reads.get()==2,"array branch reads");
            rows(run(f,Map.of("dbId","source","pending",true,"includeGovernance",false)),1);
            check(f.reads.get()==2,"pending branch reads");
            rows(run(f,Map.of("dbId","source","annotatedOnly",true,"includeGovernance",false)),count);
            rows(run(f,Map.of("dbId","source","tenantId","ct","includeGovernance",false)),count+1);
            for (String id : List.of("foreign","missing")) {
                Object denied=run(f,Map.of("dbId",id));
                check(denied instanceof ExitValue && ((ExitValue)denied).getValues()[0].toString().equals("404"),"source outside active tenant rejected");
                check(f.reads.get()==1,"reject before any table read");
            }
        }
        System.out.println("Array/pending/page/annotated snapshots exclude foreign rows; tenant input cannot override the authenticated context.");
    }
}
