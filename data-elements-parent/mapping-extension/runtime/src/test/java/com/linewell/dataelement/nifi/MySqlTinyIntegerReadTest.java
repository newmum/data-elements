package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.dbcp.DBCPService;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.processors.standard.ExecuteSQLRecord;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual SQL/JDBC -> ExecuteSQLRecord -> JSON, with the native MySQL TINYINT metadata seen on the node. */
class MySqlTinyIntegerReadTest {
    @Test void signedAndUnsignedTinyintJdbcCarriersKeepFullValuesThroughActualRecordSerialization() throws Exception {
        for(int count:List.of(20,100)) {
            String url="jdbc:h2:mem:validation_mysql_integer_"+count+";DB_CLOSE_DELAY=-1";
            Integer[] signed={-128,-1,0,1,2,127,null},unsigned={0,1,2,127,128,255,null};
            try(var db=DriverManager.getConnection(url)) {
                db.createStatement().execute("CREATE TABLE VALIDATION_SOURCE(ID INT PRIMARY KEY,SIGNED_VALUE TINYINT,UNSIGNED_VALUE SMALLINT CHECK(UNSIGNED_VALUE BETWEEN 0 AND 255))");
                try(var insert=db.prepareStatement("INSERT INTO VALIDATION_SOURCE VALUES(?,?,?)")) {
                    for(int i=0;i<count;i++){insert.setInt(1,i);insert.setObject(2,signed[i%signed.length],Types.TINYINT);insert.setObject(3,unsigned[i%unsigned.length],Types.SMALLINT);insert.addBatch();}insert.executeBatch();
                }
            }
            var runner=TestRunners.newTestRunner(ExecuteSQLRecord.class);var pool=new Pool(url);runner.addControllerService("pool",pool);runner.enableControllerService(pool);
            var writer=new JsonRecordSetWriter();runner.addControllerService("writer",writer);runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");runner.setProperty(writer,"Schema Write Strategy","no-schema");runner.enableControllerService(writer);
            runner.setProperty("Database Connection Pooling Service","pool");runner.setProperty("Record Writer","writer");runner.setProperty("Normalize Table/Column Names","false");runner.setProperty("Use Avro Logical Types","true");
            runner.enqueue("SELECT ID,SIGNED_VALUE,UNSIGNED_VALUE FROM VALIDATION_SOURCE ORDER BY ID");runner.run();runner.assertTransferCount("failure",0);runner.assertTransferCount("success",1);
            var output=new ObjectMapper().readTree(runner.getFlowFilesForRelationship("success").getFirst().toByteArray());assertEquals(count,output.size());
            for(int id=0;id<count;id++) {var row=output.get(id);assertEquals(id,row.path("ID").asInt());Integer s=signed[id%signed.length],u=unsigned[id%unsigned.length];
                if(s==null)assertTrue(row.path("SIGNED_VALUE").isNull());else {assertTrue(row.path("SIGNED_VALUE").isIntegralNumber());assertEquals(s.intValue(),row.path("SIGNED_VALUE").intValue());}
                if(u==null)assertTrue(row.path("UNSIGNED_VALUE").isNull());else {assertTrue(row.path("UNSIGNED_VALUE").isIntegralNumber());assertEquals(u.intValue(),row.path("UNSIGNED_VALUE").intValue());}
            }
            runner.shutdown();
        }
    }
    public static class Pool extends AbstractControllerService implements DBCPService {
        private final String url;Pool(String url){this.url=url;}
        @Override public Connection getConnection(){try{return (Connection)wrap(DriverManager.getConnection(url));}catch(SQLException error){throw new IllegalStateException("Isolated SQL fixture unavailable",error);}}
    }
    private static Object wrap(Object value) {
        Class<?> type=value instanceof Connection?Connection.class:value instanceof PreparedStatement?PreparedStatement.class:
                value instanceof Statement?Statement.class:value instanceof ResultSet?ResultSet.class:value instanceof ResultSetMetaData?ResultSetMetaData.class:null;
        if(type==null)return value;
        return Proxy.newProxyInstance(MySqlTinyIntegerReadTest.class.getClassLoader(),new Class[]{type},(proxy,method,args)->{
            // H2 has no unsigned TINYINT. Store 0..255 in a checked SMALLINT;
            // expose the observed MySQL TINYINT/Integer metadata, preserving real JDBC getters.
            if(value instanceof ResultSetMetaData && args!=null && args.length==1 && args[0] instanceof Integer column && column>1) {
                switch(method.getName()) {
                    case "getColumnType":return Types.TINYINT;
                    case "getColumnTypeName":return column==3?"TINYINT UNSIGNED":"TINYINT";
                    case "getColumnClassName":return "java.lang.Integer";
                    case "getPrecision":return 3;
                    case "getScale":return 0;
                    case "isSigned":return column!=3;
                }
            }
            try{return wrap(method.invoke(value,args));}catch(InvocationTargetException error){throw error.getCause();}
        });
    }
}