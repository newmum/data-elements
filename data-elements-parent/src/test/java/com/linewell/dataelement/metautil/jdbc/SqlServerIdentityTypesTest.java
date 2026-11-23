package com.linewell.dataelement.metautil.jdbc;

import com.linewell.dataelement.metautil.ddl.PortableColumnType;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SqlServerIdentityTypesTest {
    @ParameterizedTest @CsvSource(delimiter='|',value={
            "int identity|int|NUMBER(10)","bigint IDENTITY|bigint|NUMBER(19)",
            "smallint identity (1,-1)|smallint|NUMBER(5)","tinyint identity|tinyint|NUMBER(5)",
            "DECIMAL ( 18 , 0 ) IDENTITY(100,1)|DECIMAL(18,0)|NUMBER(18,0)",
            "numeric(38,0) identity(-1,2)|numeric(38,0)|NUMBER(38,0)"})
    void identityIsSeparateFromTheBoundedNumericTypeAndSeedNeverBecomesPrecision(String raw,String base,String expected) {
        assertTrue(SqlServerIdentityTypes.hasIdentity(raw));assertEquals(base,SqlServerIdentityTypes.baseType(raw));
        var column=new ColumnInfo();column.setSourceDatabaseType("sqlserver");
        assertEquals(expected,PortableColumnType.render(DatabaseType.ORACLE,column,raw));
        assertNotEquals(PortableColumnType.Kind.UNKNOWN,PortableColumnType.kind(raw,"mssql"));
        assertEquals(raw,SqlServerIdentityTypes.forSource(raw,"postgresql"));
    }
    @ParameterizedTest @ValueSource(strings={"varchar(20) identity","int identity; SELECT 1","int identity -- comment",
            "int identity(1,0)","numeric(18,2) identity","numeric(39,0) identity","int identity(1,1) NOT NULL","intidentity"})
    void unrelatedTypesSqlFragmentsAndInvalidIdentityDeclarationsAreNotAccepted(String raw) {
        assertFalse(SqlServerIdentityTypes.hasIdentity(raw));assertEquals(raw,SqlServerIdentityTypes.baseType(raw));
    }
    @Test void onlyTheActualSqlServerConnectionGetsTheLegacyNormalization() {
        assertEquals("int",SqlServerIdentityTypes.forConnection("int identity",Map.of("dbType","SQL_SERVER")));
        assertEquals("int",SqlServerIdentityTypes.forConnection("int identity",Map.of("jdbcURL","jdbc:sqlserver://validation")));
        assertEquals("int identity",SqlServerIdentityTypes.forConnection("int identity",Map.of("dbType","postgresql","jdbcURL","jdbc:sqlserver://validation")));
        assertNull(SqlServerIdentityTypes.baseType(null));assertEquals("int",SqlServerIdentityTypes.baseType("int"));
        assertEquals(PortableColumnType.Kind.UNKNOWN,PortableColumnType.kind("int identity","postgresql"));
    }
}
