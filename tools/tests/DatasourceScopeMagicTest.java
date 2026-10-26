import java.nio.file.*;
import java.util.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Runs the edited Magic sources, including bounded batch reads with a denied menu scope. */
public class DatasourceScopeMagicTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static String body(Path path) throws Exception {
        String source = Files.readString(path);
        return source.substring(source.indexOf("================================") + 32);
    }
    static Object read(PushRegistrySyncMagicTest.Fixture f, String script, String ids) {
        f.reads.set(0); f.writes.set(0);
        var vars = new HashMap<String,Object>();
        vars.put("db", f.db); vars.put("tenantRuntime", f.scope); vars.put("dataScope", f.scope);
        vars.put("tid", ids); vars.put("snapshotOnly", true);
        return MagicScript.create(script, null).execute(new MagicScriptContext(vars));
    }
    public static void main(String[] args) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<String> files = mapper.readValue(Files.readString(Path.of(args[0])), List.class);
        for (String file : files) {
            String script = body(Path.of(file));
            check(!script.contains("dataScope."), "source-role gate remains: " + file);
            MagicScript.create(script, null).compile();
        }
        System.out.println(files.size() + " canonical Magic sources compile; no datasource menu-scope calls remain.");
        String script = body(Path.of("magic/api/02.数据盘点/02.数据库表/01.元数据管理/07.获取字段列表.ms"))
            // Physical connection modules are not used by this saved-snapshot branch.
            .replaceAll("(?m)^import (tenantRuntime|jsons|'.*' as datasourceConnectionConfig)\\r?\\n", "");
        for (int count : List.of(20, 100)) {
            var f = new PushRegistrySyncMagicTest.Fixture(count);
            f.scope.denied = true;
            f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,is_del) values('foreign-table','ct','foreign',0)");
            f.jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,is_del) values('foreign-column','ct','table_0','foreign-secret',0)");
            String ids = java.util.stream.IntStream.range(0,count).mapToObj(i -> "table_" + i).collect(java.util.stream.Collectors.joining(","));
            Object result = read(f, script, ids);
            check(result instanceof Map, "metadata menu role must not block fields: " + result);
            var groups = (Map<?,?>) result;
            check(groups.size() == count, "one field group per requested table");
            for (Object value : groups.values()) {
                var rows = (List<?>) value;
                check(rows.size() == 1 && !rows.toString().contains("foreign-secret"), "foreign column leaked");
            }
            check(f.reads.get() == 3 && f.writes.get() == 0, "batch SQL count must stay at 3");
            Object denied = read(f, script, ids.substring(0, ids.lastIndexOf(',') + 1) + "foreign-table");
            check(denied instanceof ExitValue && ((ExitValue)denied).getValues()[0].toString().equals("404"), "foreign ID must reject entire batch");
            check(f.writes.get() == 0, "rejected batch writes nothing");
            System.out.println(count + " table IDs: one request, 3 SQL reads; foreign rows excluded and invalid batches rejected.");
        }
    }
}
