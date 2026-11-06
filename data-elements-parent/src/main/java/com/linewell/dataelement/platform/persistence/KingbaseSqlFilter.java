package com.linewell.dataelement.platform.persistence;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.*;
import java.sql.SQLException;

/** Installed only on Kingbase tenant pools; control/MySQL/DM pools are unaffected. */
public final class KingbaseSqlFilter extends FilterAdapter {
    @Override public PreparedStatementProxy connection_prepareStatement(FilterChain c,ConnectionProxy p,String s)throws SQLException{return c.connection_prepareStatement(p,KingbaseSql.adapt(s));}
    @Override public PreparedStatementProxy connection_prepareStatement(FilterChain c,ConnectionProxy p,String s,int a)throws SQLException{return c.connection_prepareStatement(p,KingbaseSql.adapt(s),a);}
    @Override public PreparedStatementProxy connection_prepareStatement(FilterChain c,ConnectionProxy p,String s,int a,int b)throws SQLException{return c.connection_prepareStatement(p,KingbaseSql.adapt(s),a,b);}
    @Override public PreparedStatementProxy connection_prepareStatement(FilterChain c,ConnectionProxy p,String s,int a,int b,int d)throws SQLException{return c.connection_prepareStatement(p,KingbaseSql.adapt(s),a,b,d);}
    @Override public PreparedStatementProxy connection_prepareStatement(FilterChain c,ConnectionProxy p,String s,int[] a)throws SQLException{return c.connection_prepareStatement(p,KingbaseSql.adapt(s),a);}
    @Override public PreparedStatementProxy connection_prepareStatement(FilterChain c,ConnectionProxy p,String s,String[] a)throws SQLException{return c.connection_prepareStatement(p,KingbaseSql.adapt(s),a);}
    @Override public ResultSetProxy statement_executeQuery(FilterChain c,StatementProxy p,String s)throws SQLException{return c.statement_executeQuery(p,KingbaseSql.adapt(s));}
    @Override public int statement_executeUpdate(FilterChain c,StatementProxy p,String s)throws SQLException{return c.statement_executeUpdate(p,KingbaseSql.adapt(s));}
    @Override public boolean statement_execute(FilterChain c,StatementProxy p,String s)throws SQLException{return c.statement_execute(p,KingbaseSql.adapt(s));}
    private static Object value(Object v){return v!=null && v.getClass().getName().equals("com.kingbase8.util.KBobject")?v.toString():v;}
    @Override public Object resultSet_getObject(FilterChain c,ResultSetProxy p,int index)throws SQLException{return value(c.resultSet_getObject(p,index));}
    @Override public Object resultSet_getObject(FilterChain c,ResultSetProxy p,String name)throws SQLException{return value(c.resultSet_getObject(p,name));}
}
