package com.linewell.dataelement.metautil.query;

import static org.junit.jupiter.api.Assertions.*;
import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.query.impl.*;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AdditionalDatabaseQueryTest {
    @ParameterizedTest
    @EnumSource(value=DatabaseType.class,names={"SQLSERVER","GBASE8A","VERTICA","HIVE","GAUSSDB"})
    void previewTwentyAndHundredRowsUsesFixedQueriesAndPreservesDialect(DatabaseType type) throws Exception {
        String url="jdbc:h2:mem:validation_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;MODE="+(type==DatabaseType.SQLSERVER?"MSSQLServer":"PostgreSQL");
        try(Connection setup=DriverManager.getConnection(url)) {
            setup.createStatement().execute("CREATE TABLE PUBLIC.orders(id INT PRIMARY KEY, label VARCHAR(100))");
            try(var insert=setup.prepareStatement("INSERT INTO PUBLIC.orders VALUES(?,?)")) {
                for(int i=1;i<=120;i++){insert.setInt(1,i);insert.setString(2,"数据"+i);insert.addBatch();}
                insert.executeBatch();
            }
        }
        AtomicInteger sql=new AtomicInteger();
        java.util.function.Supplier<Connection> connections=()->{
            try {
                Connection real=DriverManager.getConnection(url);
                return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
                    if(method.getName().equals("prepareStatement"))sql.incrementAndGet();
                    try{return method.invoke(real,args);}catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
                });
            } catch(Exception error){throw new IllegalStateException(error);}
        };
        DataQueryExecutor executor=switch(type) {
            case SQLSERVER -> new SqlServerDataQueryExecutor(){@Override protected Connection getConnection(DataSourceConfig c){return connections.get();}};
            case GBASE8A -> new GBase8aDataQueryExecutor(){@Override protected Connection getConnection(DataSourceConfig c){return connections.get();}};
            case VERTICA -> new VerticaDataQueryExecutor(){@Override protected Connection getConnection(DataSourceConfig c){return connections.get();}};
            case HIVE -> new HiveDataQueryExecutor(){@Override protected Connection getConnection(DataSourceConfig c){return connections.get();}};
            case GAUSSDB -> new MySqlLikeDataQueryExecutor(){@Override protected Connection getConnection(DataSourceConfig c){return connections.get();}};
            default -> throw new IllegalStateException();
        };
        DataSourceConfig source=new DataSourceConfig();source.setDatabaseType(type);source.setDatabase("PUBLIC");source.setSchema("PUBLIC");
        if(type==DatabaseType.GAUSSDB)source.setGaussdbVariant("mysql");
        PageQueryRequest request=new PageQueryRequest();request.setDataSource(source);request.setTableName("ORDERS");request.setPageNo(1);
        var sort=new PageQuerySort();sort.setField("id");sort.setDirection("ASC");request.setSorts(List.of(sort));
        Integer firstCount=null;
        for(int size:new int[]{20,100}) {
            sql.set(0);request.setPageSize(size);PageQueryResult result=executor.queryPage(request);
            assertEquals(120L,result.getTotal());assertEquals(size,result.getRows().size());
            assertTrue(sql.get()>=2 && sql.get()<=3,"仅允许字段兜底、总数及当前页查询");
            if(firstCount==null)firstCount=sql.get();else assertEquals(firstCount.intValue(),sql.get(),"20/100行查询数必须保持一致");
        }
        request.setPageSize(20);request.setPageNo(2);var second=executor.queryPage(request);
        assertEquals(20,second.getRows().size());assertEquals(21,((Number)second.getRows().getFirst().values().iterator().next()).intValue());
        var invalid=new PageQuerySort();invalid.setField("outside_permission");invalid.setDirection("ASC");request.setSorts(List.of(invalid));
        assertThrows(RuntimeException.class,()->executor.queryPage(request));
    }
}
