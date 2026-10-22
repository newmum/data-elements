package com.linewell.dataelement.metautil.ddl;

import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TablePartitionsTest {
    @Test void yearAndMonthPlansCanCoverEveryPeriodThroughDecember2035() {
        for(var type:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE,DatabaseType.DAMENG,DatabaseType.HIVE,
                DatabaseType.MYSQL,DatabaseType.MARIADB,DatabaseType.OCEANBASE_MYSQL)) {
            for(var request:List.of(
                    Map.<String,Object>of("enabled",true,"strategy","YEAR","field","created_at","startYear","2026","years",10),
                    Map.<String,Object>of("enabled",true,"strategy","MONTH","field","created_at","startMonth","2026-10","months",111))) {
                var plan=service.normalize(request,List.of(column("created_at","DATE")),type);
                var parts=(List<?>)plan.get("partitions");
                assertEquals("2036-01-01 00:00:00",((Map<?,?>)parts.get(parts.size()-2)).get("upper"));
                assertEquals("MAXVALUE",((Map<?,?>)parts.getLast()).get("upper"));
            }
        }
    }
    private final TablePartitions service=new TablePartitions();
    private static ColumnInfo column(String name,String type){var c=new ColumnInfo();c.setColumnName(name);c.setColumnType(type);return c;}
    private static DataSourceConfig config(DatabaseType type){var c=new DataSourceConfig();c.setDatabaseType(type);c.setUsername("APP");c.setDatabase("ods");return c;}
    private static Map<String,Object> month(){return new LinkedHashMap<>(Map.of("enabled",true,"strategy","MONTH","field","created_at","startMonth","2026-01","months",2));}
    @ParameterizedTest
    @EnumSource(value=DatabaseType.class,names={"ORACLE","OCEANBASE_ORACLE","DAMENG","HIVE","MYSQL","MARIADB","OCEANBASE_MYSQL"})
    void everyVisibleDialectCanRoundTripItsGeneratedPlan(DatabaseType type) {
        var cfg=config(type);var columns=List.of(column("created_at","DATE"));
        var plan=service.normalize(month(),columns,type);
        String ddl=service.apply("CREATE TABLE ods_t(created_at DATE);",plan,cfg);
        assertDoesNotThrow(()->service.validate(ddl,new LinkedHashMap<>(plan),cfg));
    }
    @Test void monthlyPreviewAndExecutionUseIdenticalBoundariesAndCancelRestoresOrdinaryTable() {
        var cfg=config(DatabaseType.ORACLE);
        String base="CREATE TABLE ODS_T (id NUMBER, created_at DATE); COMMENT ON TABLE ODS_T IS '业务表';";
        var plan=service.normalize(month(),List.of(column("id","NUMBER"),column("created_at","DATE")),cfg.getDatabaseType());
        String ddl=service.apply(base,plan,cfg);
        assertTrue(ddl.contains("PARTITION BY RANGE (created_at)"));
        assertTrue(ddl.contains("2026-02-01"));assertTrue(ddl.contains("MAXVALUE"));
        assertFalse(ddl.contains("\"created_at\""),"unquoted Oracle columns must retain Oracle name folding");
        String safe=service.validate(ddl,new LinkedHashMap<>(plan),cfg);
        assertFalse(safe.contains("PARTITION BY"));
        assertTrue(new CanvasTableDdl().matches(new CanvasTableDdl().parse(safe,cfg),"ODS_T","APP"));
        assertThrows(IllegalArgumentException.class,()->service.validate(ddl,Map.of("enabled",false),cfg));
        assertEquals(base,service.apply(base,Map.of("enabled",false),cfg));
        assertThrows(IllegalArgumentException.class,()->service.validate(ddl.replace("2026-02-01","2026-02-02"),new LinkedHashMap<>(plan),cfg));
    }
    @Test void hiveCreatesOnlyItsOwnPartitionRegistrationStatements() {
        var cfg=config(DatabaseType.HIVE);
        String base="CREATE TABLE ods_t (id BIGINT, created_at TIMESTAMP) ROW FORMAT DELIMITED FIELDS TERMINATED BY '\\001' STORED AS TEXTFILE;";
        var plan=service.normalize(month(),List.of(column("id","BIGINT"),column("created_at","TIMESTAMP")),DatabaseType.HIVE);
        String ddl=service.apply(base,plan,cfg);
        assertTrue(ddl.indexOf("PARTITIONED BY")<ddl.indexOf("ROW FORMAT"));
        assertTrue(ddl.contains("'202601'"));assertTrue(ddl.contains("ADD IF NOT EXISTS PARTITION"));
        assertDoesNotThrow(()->service.validate(ddl,new LinkedHashMap<>(plan),cfg),()-> {
            var ast=(com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement)PortableSqlParser.parse(ddl,DatabaseType.HIVE).getFirst();
            var clone=ast.clone();clone.setPartitioning(null);clone.getPartitionColumns().clear();
            return "actual="+ddl+" expected="+service.apply(com.alibaba.druid.sql.SQLUtils.toSQLString(clone,com.alibaba.druid.DbType.hive)+";",plan,cfg);
        });
        assertThrows(IllegalArgumentException.class,()->service.validate(ddl+"DROP TABLE other;",new LinkedHashMap<>(plan),cfg));
        assertThrows(IllegalArgumentException.class,()->service.validate(ddl.replace("ALTER TABLE ods_t","ALTER TABLE other"),new LinkedHashMap<>(plan),cfg));
    }
    @Test void listValuesFollowColumnTypeAndKeepSourceGrouping() {
        var cfg=config(DatabaseType.ORACLE);
        var raw=new LinkedHashMap<String,Object>(Map.of("enabled",true,"field","region_code","strategy","LIST","partitions",List.of(
                Map.of("name","p_one","bucket","350100","values",List.of("350100","350200")),Map.of("name","p_other","bucket","other","default",true))));
        var plan=service.normalize(raw,List.of(column("region_code","VARCHAR2(6)")),DatabaseType.ORACLE);
        String ddl=service.apply("CREATE TABLE ods_t(region_code VARCHAR2(6));",plan,cfg);
        assertTrue(ddl.contains("'350100', '350200'"));assertTrue(ddl.contains("VALUES (DEFAULT)"));
        assertDoesNotThrow(()->service.validate(ddl,new LinkedHashMap<>(plan),cfg));
        raw.put("partitions",List.of(Map.of("name","p_one","bucket","../bad","values",List.of("350100"))));
        assertThrows(IllegalArgumentException.class,()->service.normalize(raw,List.of(column("region_code","VARCHAR2(6)")),DatabaseType.HIVE));
    }
    @Test void partitionPreviewLeavesKeyDesignAndServerCompatibilityToDatabase() {
        var id=column("id","BIGINT");id.setPrimaryKey(true);
        for (var type : List.of(DatabaseType.MYSQL,DatabaseType.MARIADB,DatabaseType.OCEANBASE_MYSQL,DatabaseType.OCEANBASE_ORACLE)) {
            var plan=assertDoesNotThrow(()->service.normalize(month(),List.of(id,column("created_at","DATE")),type));
            String ddl=service.apply("CREATE TABLE ods_t(id BIGINT PRIMARY KEY,created_at DATE)",plan,config(type));
            assertTrue(ddl.contains("PRIMARY KEY")); assertTrue(ddl.contains("PARTITION BY RANGE"));
            assertEquals(true,id.getPrimaryKey());
        }
        var time=column("created_at","DATE");time.setPrimaryKey(true);
        assertDoesNotThrow(()->service.normalize(month(),List.of(id,time),DatabaseType.OCEANBASE_ORACLE));
    }
    @Test void mysqlLiteralCannotEscapeItsPartitionValueThroughBackslashOrSqlMode() {
        var unsafe=new LinkedHashMap<String,Object>(Map.of("enabled",true,"strategy","LIST","field","region_code","partitions",List.of(Map.of("name","p_one","bucket","one","values",List.of("\\'; DROP TABLE another; --")))));
        assertThrows(IllegalArgumentException.class,()->service.normalize(unsafe,List.of(column("region_code","VARCHAR(50)")),DatabaseType.MYSQL));
    }
    @Test void intervalKeepsOracleNativeAndExpandsOceanbaseWithoutInvalidIntervalSyntax() {
        var raw=new LinkedHashMap<String,Object>(Map.of("enabled",true,"strategy","RANGE","field","created_at","sourceInterval",Map.of("unit","MONTH","step",1),"partitions",List.of(Map.of("name","p_history","bucket","history","upper","2026-01-01 00:00:00"))));
        var columns=List.of(column("created_at","DATE"));
        var oracle=service.normalize(raw,columns,DatabaseType.ORACLE);
        assertTrue(service.apply("CREATE TABLE ods_t(created_at DATE);",oracle,config(DatabaseType.ORACLE)).contains("INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))"));
        var ob=service.normalize(raw,columns,DatabaseType.OCEANBASE_ORACLE);
        String ddl=service.apply("CREATE TABLE ods_t(created_at DATE);",ob,config(DatabaseType.OCEANBASE_ORACLE));
        assertFalse(ddl.contains("INTERVAL"));assertTrue(ddl.contains("MAXVALUE"));
        var expanded=(List<?>)ob.get("partitions");
        assertTrue(((Map<?,?>)expanded.get(expanded.size()-2)).get("upper").toString().compareTo("2036-01-01 00:00:00")>=0,
                "Inherited month/year interval plans include the whole of 2035");
        assertDoesNotThrow(()->service.validate(ddl,new LinkedHashMap<>(ob),config(DatabaseType.OCEANBASE_ORACLE)));
    }
    @Test void explicitSourceProbeUsesFiveFixedReadsForTwentyOrHundredPartitions() throws Exception {
        for(int count:List.of(20,100)) {
            Connection c=mock(Connection.class);
            var header=statement(new String[]{"PARTITIONING_TYPE","SUBPARTITIONING_TYPE"},new String[][]{{"RANGE","NONE"}});
            var key=statement(new String[]{"COLUMN_NAME","DATA_TYPE"},new String[][]{{"CREATED_AT","DATE"}});
            String[][] partitions=new String[count][2];for(int i=0;i<count;i++)partitions[i]=new String[]{"SYS_P"+i,"TO_DATE(' "+java.time.LocalDate.of(2000,1,1).plusMonths(i)+" 00:00:00','SYYYY-MM-DD HH24:MI:SS','NLS_CALENDAR=GREGORIAN')"};
            var rows=statement(new String[]{"PARTITION_NAME","HIGH_VALUE"},partitions);
            var nativeDdl=statement(new String[]{"TABLE_DDL"},new String[][]{});
            var constraints=statement(new String[]{"CONSTRAINT_NAME","CONSTRAINT_TYPE","COLUMN_NAME"},new String[][]{});
            when(c.prepareStatement(anyString())).thenReturn(nativeDdl,header,key,rows,constraints);
            var result=PartitionMetadataReader.read(c,config(DatabaseType.ORACLE),"APP.T");
            assertEquals("detected",result.get("status"));
            assertEquals(count,((List<?>)((Map<?,?>)result.get("plan")).get("partitions")).size());
            verify(c,times(5)).prepareStatement(anyString());
            assertEquals("MONTH",((Map<?,?>)result.get("plan")).get("displayStrategy"));
            assertEquals("unavailable",result.get("ddlReadStatus"));
            verify(header).setString(1,"APP");verify(header).setString(2,"T");
        }
    }
    private static PreparedStatement statement(String[] labels,String[][] values) throws Exception {
        PreparedStatement statement=mock(PreparedStatement.class);ResultSet result=mock(ResultSet.class);ResultSetMetaData metadata=mock(ResultSetMetaData.class);
        when(statement.executeQuery()).thenReturn(result);when(result.getMetaData()).thenReturn(metadata);when(metadata.getColumnCount()).thenReturn(labels.length);
        for(int i=0;i<labels.length;i++)when(metadata.getColumnLabel(i+1)).thenReturn(labels[i]);
        var cursor=new java.util.concurrent.atomic.AtomicInteger(-1);when(result.next()).thenAnswer(call->cursor.incrementAndGet()<values.length);
        when(result.getString(anyInt())).thenAnswer(call->values[cursor.get()][call.getArgument(0,Integer.class)-1]);return statement;
    }
}
