package com.haitong.store;
import java.util.Locale;

/** No vendor SQL is assembled from request-controlled identifiers. Times are UTC epoch milliseconds. */
public enum DatabaseDialect {
    MYSQL, DM, H2;
    public static DatabaseDialect of(String type) {
        if(type==null) throw new IllegalArgumentException("db.type is required");
        return valueOf(type.toUpperCase(Locale.ROOT));
    }
    public String clockSql() {return this==DM?"SELECT CURRENT_TIMESTAMP FROM DUAL":"SELECT CURRENT_TIMESTAMP(3)";}
    public String pageSql(String orderedSql) {
        return this==DM?"SELECT * FROM (SELECT ht_q.*, ROWNUM ht_rn FROM ("+orderedSql+") ht_q WHERE ROWNUM<=?) WHERE ht_rn>? ORDER BY ht_rn":orderedSql+" LIMIT ? OFFSET ?";
    }
    public Object[] pageArgs(int limit,int offset,Object... prior) {
        if(limit<1 || limit>1000 || offset<0 || offset>1000000) throw new IllegalArgumentException("分页参数超限");
        Object[] args=java.util.Arrays.copyOf(prior,prior.length+2);
        if(this==DM)args[prior.length]=(long)limit+offset;else args[prior.length]=limit;
        args[prior.length+1]=offset;return args;
    }
    public String driver() {return switch(this){case MYSQL->"com.mysql.cj.jdbc.Driver";case DM->"dm.jdbc.driver.DmDriver";case H2->"org.h2.Driver";};}
}
