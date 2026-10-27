import java.nio.file.*;
import java.util.*;
import org.ssssssss.script.*;
import static java.util.stream.Collectors.joining;

/** Verifies the exact snapshot branch used by the step-five bounded field queue. */
public class PushContractColumnScopeTest {
    static String script() throws Exception {
        String file=Files.readString(Path.of("magic/api/02.数据盘点/02.数据库表/01.元数据管理/07.获取字段列表.ms"));
        String branch=file.substring(file.indexOf("var excludes ="),file.indexOf("    // registered table asset fallback"));
        return "var currentTenantId='ga'\nvar textOf=v=>v==null?null:v+''\nvar blank=v=>v==null||(v+'').trim()==''\n"+branch+"\n}\nreturn []";
    }
    static Object call(PushRegistrySyncMagicTest.Fixture f,String ids) throws Exception {
        f.reads.set(0);
        return MagicScript.create(script(),null).execute(new MagicScriptContext(Map.of("tid",ids,"snapshotOnly",true,"db",f.db,"dataScope",f.scope)));
    }
    public static void main(String[] args) throws Exception {
        for(int count:List.of(20,100)) {
            var f=new PushRegistrySyncMagicTest.Fixture(count);
            String ids=java.util.stream.IntStream.range(0,count).mapToObj(i->"table_"+i).collect(joining(","));
            Map<?,?> result=(Map<?,?>)call(f,ids);
            PushRegistrySyncMagicTest.check(result.size()==count,"all requested tables returned");
            PushRegistrySyncMagicTest.check(f.reads.get()==3,"snapshot reads fixed at three");
            f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values('foreign-table','ct','foreign','foreign_table','','',2,0)");
            PushRegistrySyncMagicTest.rejected(call(f,ids+",foreign-table"),count==100?400:404);
            f.jdbc.update("insert into db_datasource_t values('private','ga','PRIVATE',2,'{}',0,'private-org','other','app')");
            f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values('private-table','ga','private','private_table','','',2,0)");
            f.scope.hidden="private";
            PushRegistrySyncMagicTest.rejected(call(f,"table_0,private-table"),403);
            System.out.println(count+" table IDs: 3 SQL reads; foreign tenant and mixed unauthorized source IDs rejected as a whole");
        }
    }
}
