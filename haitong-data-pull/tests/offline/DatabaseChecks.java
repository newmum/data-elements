import com.haitong.store.DatabaseDialect;
import com.haitong.core.*;
import java.util.*;
public class DatabaseChecks {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static void fails(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException|RelayException e){return;}throw new AssertionError("expected rejection "+checks);}
    public static void main(String[] args){
        check(DatabaseDialect.of("mysql")==DatabaseDialect.MYSQL);
        check(DatabaseDialect.of("dm")==DatabaseDialect.DM);
        check(DatabaseDialect.of("H2")==DatabaseDialect.H2);
        fails(()->DatabaseDialect.of("postgresql"));fails(()->DatabaseDialect.of(null));
        check(DatabaseDialect.DM.driver().equals("dm.jdbc.driver.DmDriver"));
        check(DatabaseDialect.MYSQL.driver().equals("com.mysql.cj.jdbc.Driver"));
        check(DatabaseDialect.DM.clockSql().endsWith("FROM DUAL"));
        check(DatabaseDialect.MYSQL.clockSql().contains("CURRENT_TIMESTAMP"));
        check(DatabaseDialect.MYSQL.pageSql("SELECT id FROM ht_message ORDER BY id").endsWith("LIMIT ? OFFSET ?"));
        check(DatabaseDialect.DM.pageSql("SELECT id FROM ht_message ORDER BY id").contains("ROWNUM<=?"));
        Object[] m=DatabaseDialect.MYSQL.pageArgs(50,100,"x");check(m.length==3);check(m[0].equals("x"));check(m[1].equals(50));check(m[2].equals(100));
        Object[] d=DatabaseDialect.DM.pageArgs(50,100,"x");check(d[1].equals(150L));check(d[2].equals(100));
        fails(()->DatabaseDialect.DM.pageArgs(0,0));fails(()->DatabaseDialect.MYSQL.pageArgs(2001,0));fails(()->DatabaseDialect.H2.pageArgs(50,-1));
        Map<String,Object> input=Map.of("data",Map.of("id","order-1","number",7),"batch","B1");
        check(BusinessFields.value(input,"data.id",200).equals("order-1"));
        check(BusinessFields.value(input,"data.number",200).equals("7"));
        check(BusinessFields.value(input,"batch",200).equals("B1"));
        check(BusinessFields.value(input,"missing",200)==null);check(BusinessFields.value(input,"data.missing",200)==null);
        check(BusinessFields.value(input,"",200)==null);check(BusinessFields.value(input,null,200)==null);
        fails(()->BusinessFields.value(input,"data",200));fails(()->BusinessFields.value(input,"data.id",2));
        fails(()->BusinessFields.value(input,"data..id",200));
        System.out.println("DATABASE/IDENTITY JDK CHECKS PASSED: "+checks+" (no database connection)");
    }
}
