package com.linewell.dataelement.metautil.ddl;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.metautil.explorer.impl.MySqlMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class PortableStringCapacityTest {
    private final PortableDdlConverter converter=new PortableDdlConverter();
    private static ColumnInfo column(String source,String raw,long length) {
        var c=new ColumnInfo();c.setColumnName("description");c.setSourceDatabaseType(source);c.setColumnType(raw);c.setLength(length);return c;
    }
    private static DataSourceConfig config(DatabaseType type) {
        var c=new DataSourceConfig();c.setDatabaseType(type);c.setDatabase("ods");c.setSchema("ODS");return c;
    }
    @Test void characterBoundsFromAllHighRiskSourcesRemainCharacterBoundsInOracleTargets() {
        for(String source:List.of("postgresql","mysql","mariadb","sqlserver","dm","kingbasees","gaussdb","openGauss","oracle","oceanbaseoracle"))
            for(var target:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE)) {
                var c=column(source,"VARCHAR(50)",50);
                assertEquals("VARCHAR2(50 CHAR)",PortableColumnType.render(target,c,c.getColumnType()),source+" -> "+target);
            }
        assertEquals(150,"中".repeat(50).getBytes(StandardCharsets.UTF_8).length);
        assertEquals(200,"😀".repeat(50).getBytes(StandardCharsets.UTF_8).length);
    }
    @Test void byteCeilingsApplyEvenWithCharSemanticsAndOracleDiffersFromOceanbase() {
        for(var target:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE)) {
            int safe=target==DatabaseType.ORACLE?1000:8191;
            assertEquals("VARCHAR2("+safe+" CHAR)",PortableColumnType.render(target,column("postgresql","VARCHAR",safe),"VARCHAR"));
            assertEquals("CLOB",PortableColumnType.render(target,column("postgresql","VARCHAR",safe+1),"VARCHAR"));
            assertEquals("CHAR(500 CHAR)",PortableColumnType.render(target,column("postgresql","CHAR",500),"CHAR"));
            assertEquals("VARCHAR2(501 CHAR)",PortableColumnType.render(target,column("postgresql","CHAR",501),"CHAR"));
        }
        assertEquals(4000,"😀".repeat(1000).getBytes(StandardCharsets.UTF_8).length);
    }
    @Test void unboundedAndNationalTextCannotSilentlyShrinkTo255Characters() {
        for(var target:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE,DatabaseType.DAMENG)) {
            for(String raw:List.of("TEXT","LONGTEXT","MEDIUMTEXT","CLOB","VARCHAR"))
                assertEquals("CLOB",PortableColumnType.render(target,column("postgresql",raw,0),raw));
            for(String raw:List.of("NTEXT","NVARCHAR(MAX)","NCLOB"))assertEquals("NCLOB",PortableColumnType.render(target,column("sqlserver",raw,0),raw));
            assertEquals("CLOB",PortableColumnType.render(target,column("sqlite","VARCHAR(12)",12),"VARCHAR(12)"));
        }
    }
    @Test void metadataKeepsByteAndCharacterLengthsSeparateAndRenderingDoesNotRepeatExpansion()throws Exception {
        var c=column("oracle","VARCHAR2(50 CHAR)",50);c.setLengthSemantics("CHAR");c.setCharacterLength(50L);c.setByteLength(200L);
        assertEquals("VARCHAR2(50 CHAR)",PortableColumnType.render(DatabaseType.OCEANBASE_ORACLE,c,c.getColumnType()));
        c.setLength(200L);assertEquals("VARCHAR2(200 CHAR)",PortableColumnType.render(DatabaseType.OCEANBASE_ORACLE,c,c.getColumnType()),"Editing length must override the source's old byte capacity");
        for(var target:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE,DatabaseType.DAMENG,DatabaseType.SQLSERVER,DatabaseType.DB2)) {
            var cfg=config(target);var original=column("postgresql","VARCHAR(50)",50);
            var first=(List<ColumnInfo>)converter.prepare(List.of(original),cfg).get("columns");
            var second=(List<ColumnInfo>)converter.prepare(first,cfg).get("columns");
            assertEquals(first.getFirst().getColumnType(),second.getFirst().getColumnType());
            String ddl=new MySqlMetadataExplorer().generateCreateTableDdl(cfg,"ODS_STRING",first,"");
            assertThat(ddl).contains(first.getFirst().getColumnType());
            var mapper=new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            var row=((List<?>)converter.items(first)).getFirst();
            var roundtrip=mapper.convertValue(row,ColumnInfo.class);
            assertEquals(first.getFirst().getColumnType(),((List<ColumnInfo>)converter.prepare(List.of(roundtrip),cfg).get("columns")).getFirst().getColumnType(),target.toString());
            assertEquals("VARCHAR(50)",original.getColumnType());assertEquals(50,original.getLength());
        }
    }
    @Test void sqlServerUsesUtf16CapacityAndByteTargetsExpandOnlyOnce() {
        assertEquals("NVARCHAR(100)",PortableColumnType.render(DatabaseType.SQLSERVER,column("postgresql","VARCHAR(50)",50),"VARCHAR(50)"));
        assertEquals("NVARCHAR(100)",PortableColumnType.render(DatabaseType.SQLSERVER,column("sqlserver","NVARCHAR(100)",100),"NVARCHAR(100)"));
        assertEquals("NVARCHAR(MAX)",PortableColumnType.render(DatabaseType.SQLSERVER,column("postgresql","VARCHAR(2001)",2001),"VARCHAR(2001)"));
        assertEquals("VARCHAR2(50 CHAR)",PortableColumnType.render(DatabaseType.DAMENG,column("postgresql","VARCHAR(50)",50),"VARCHAR(50)"));
        assertEquals("VARCHAR(200 OCTETS)",PortableColumnType.render(DatabaseType.DB2,column("postgresql","VARCHAR(50)",50),"VARCHAR(50)"));
    }
    @Test void explicitTargetCharacterSetsAffectCapacityAndIncompatibleEncodingsAreVisible() {
        var cfg=config(DatabaseType.ORACLE);cfg.setCharset("AL32UTF8");
        assertEquals("CLOB",PortableColumnType.render(cfg,column("postgresql","VARCHAR(2000)",2000),"VARCHAR(2000)"));
        cfg.setCharset("WE8ISO8859P1");
        var result=converter.prepare(List.of(column("postgresql","VARCHAR(2000)",2000)),cfg);
        assertEquals("VARCHAR2(2000 CHAR)",((List<ColumnInfo>)result.get("columns")).getFirst().getColumnType());
        assertTrue(((List<Map<String,Object>>)result.get("warnings")).stream().anyMatch(w->Boolean.TRUE.equals(w.get("requiresEdit"))));
        cfg.setCharset("UTF8");assertEquals("VARCHAR2(1000 CHAR)",PortableColumnType.render(cfg,column("postgresql","VARCHAR(500)",500),"VARCHAR(500)"));
        cfg.setNationalCharset("AL16UTF16");assertEquals("NVARCHAR2(2000)",PortableColumnType.render(cfg,column("sqlserver","NVARCHAR(2000)",2000),"NVARCHAR(2000)"));
    }
    @Test void mysqlAccountsForTheWholeRowAndNeverChangesAKeyToLobSilently() {
        var columns=new ArrayList<ColumnInfo>();var key=column("postgresql","VARCHAR(32)",32);key.setColumnName("id");key.setPrimaryKey(true);columns.add(key);
        for(int i=0;i<100;i++){var c=column("postgresql","VARCHAR(255)",255);c.setColumnName("f"+i);columns.add(c);}
        var prepared=converter.prepare(columns,DatabaseType.MYSQL);var output=(List<ColumnInfo>)prepared.get("columns");
        assertEquals("VARCHAR(32)",output.getFirst().getColumnType());assertTrue(output.stream().anyMatch(c->c.getColumnType().equals("LONGTEXT")));
        assertEquals("VARCHAR(255)",columns.getLast().getColumnType());
        var defaultText=column("postgresql","TEXT",0);defaultText.setDefaultValue("'unchanged'");
        assertTrue(((List<Map<String,Object>>)converter.prepare(List.of(defaultText),DatabaseType.MYSQL).get("warnings")).stream().anyMatch(w->Boolean.TRUE.equals(w.get("requiresEdit"))));
        var huge=column("postgresql","VARCHAR(1001)",1001);huge.setPrimaryKey(true);
        assertTrue(((List<Map<String,Object>>)converter.prepare(List.of(huge),DatabaseType.ORACLE).get("warnings")).stream().anyMatch(w->Boolean.TRUE.equals(w.get("requiresEdit"))));
    }
    @Test void generatedDdlAndExecutionParserPreserveCharUnitsForTwentyAndHundredColumns() {
        for(int count:List.of(20,100))for(var target:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE,DatabaseType.DAMENG,DatabaseType.SQLSERVER,DatabaseType.DB2,DatabaseType.MYSQL)) {
            var original=new ArrayList<ColumnInfo>();for(int i=0;i<count;i++){var c=column("postgresql","VARCHAR(50)",50);c.setColumnName("f"+i);original.add(c);}
            var cfg=config(target);var columns=(List<ColumnInfo>)converter.prepare(original,cfg).get("columns");
            String ddl=new MySqlMetadataExplorer().generateCreateTableDdl(cfg,"ODS_STRING",columns,"");
            String executed=new CanvasTableDdl().parse(ddl,cfg).get("ddl").toString();
            String fragment=switch(target){case SQLSERVER->"NVARCHAR(100)";case DB2->"VARCHAR(200OCTETS)";case MYSQL->"VARCHAR(50)";default->"50CHAR";};
            assertEquals(count,executed.replaceAll("\\s+","").split(java.util.regex.Pattern.quote(fragment),-1).length-1,target.toString());
        }
    }
    @Test void physicalCreationLetsDatabaseReportLobKeyConstraints() throws Exception {
        for(var target:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE,DatabaseType.DAMENG)) {
            for(String constraint:List.of("id CLOB PRIMARY KEY","id CLOB UNIQUE","id CLOB, PRIMARY KEY(id)","id NCLOB, UNIQUE(id)")) {
                var connection=org.mockito.Mockito.mock(java.sql.Connection.class);
                var statement=org.mockito.Mockito.mock(java.sql.Statement.class);
                org.mockito.Mockito.when(connection.createStatement()).thenReturn(statement);
                String ddl="CREATE TABLE invalid_capacity("+constraint+")";
                var databaseError=new java.sql.SQLException("ORA-02329: datatype cannot be unique or a primary key", "99999",2329);
                org.mockito.Mockito.when(statement.execute(ddl)).thenThrow(databaseError);
                var explorer=new MySqlMetadataExplorer(){@Override protected java.sql.Connection getConnection(DataSourceConfig ignored){return connection;}};
                var error=assertThrows(RuntimeException.class,()->explorer.createTable(config(target),ddl));
                assertSame(databaseError,error.getCause());
                assertTrue(error.getMessage().contains("ORA-02329"));
                org.mockito.Mockito.verify(statement).execute(ddl);
            }
        }
    }
    @Test void db2UnitsSurviveParsingWithoutRewritingDefaultsOrOtherTypes() {
        String ddl="CREATE TABLE ODS.TEST_UNITS (a VARCHAR(200 OCTETS) DEFAULT 'VARCHAR(9 OCTETS)', b CHAR(50 CODEUNITS32), c DECIMAL(12,2), d CHARACTER VARYING(25 OCTETS))";
        var parsed=new CanvasTableDdl().parse(ddl,config(DatabaseType.DB2));
        assertThat(parsed.get("ddl").toString()).contains("VARCHAR(200 OCTETS)","CHAR(50 CODEUNITS32)","'VARCHAR(9 OCTETS)'","DECIMAL(12, 2)","VARCHAR(25 OCTETS)");
        var columns=(List<ColumnInfo>)converter.read(ddl,"db2").get("columns");
        assertEquals(200L,columns.getFirst().getLength());
        assertEquals("VARCHAR(200 OCTETS)",columns.getFirst().getColumnType());
        var ast=(com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement)PortableSqlParser.parse(ddl,DatabaseType.DB2).getFirst();
        var cloned=ast.getColumnDefinitions().getFirst().getDataType().clone();
        assertEquals(1,cloned.getArguments().size());assertEquals("VARCHAR(200 OCTETS)",cloned.toString());
        assertEquals("VARCHAR2(200 CHAR)",PortableColumnType.render(DatabaseType.ORACLE,columns.getFirst(),columns.getFirst().getColumnType()));
        assertThrows(IllegalArgumentException.class,()->new CanvasTableDdl().parse(ddl+"; DROP TABLE ODS.TEST_UNITS",config(DatabaseType.DB2)));
    }
}
