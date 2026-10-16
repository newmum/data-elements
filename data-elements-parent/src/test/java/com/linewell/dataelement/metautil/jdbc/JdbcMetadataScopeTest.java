package com.linewell.dataelement.metautil.jdbc;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JdbcMetadataScopeTest {
    @Test void configuredAndLiveSchemaAvoidAllFallbackQueries()throws Exception {
        var connection=mock(Connection.class);
        assertEquals("A.B",JdbcMetadataScope.schema(connection,Map.of("schema","\"A.B\"","defaultSchema","OTHER"),null));
        verifyNoInteractions(connection);
        when(connection.getSchema()).thenReturn("ACTIVE");
        assertEquals("ACTIVE",JdbcMetadataScope.schema(connection,Map.of("dbType","oracle","username","LOGIN"),null));
        verify(connection,never()).prepareStatement(anyString());
    }
    @Test void oracleRegistrationAliasesAndLegacyDriversUseOneBoundedSessionRead()throws Exception {
        for(var config:List.of(Map.of("dbType","oracle12+"),Map.of("dbType","oceanbase_compatible_oracle"),
                Map.of("url","jdbc:oceanbaseoracle://host/service"),Map.of("dbType","oceanbase","compatibilityMode","ORACLE"))) {
            var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);var statement=mock(PreparedStatement.class);var rows=mock(ResultSet.class);
            when(connection.getMetaData()).thenReturn(metadata);when(metadata.getDatabaseProductName()).thenReturn("OceanBase");
            when(connection.getSchema()).thenThrow(new SQLException("Unsupported method","0A000"));
            when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenReturn(rows);when(rows.next()).thenReturn(true);when(rows.getString(1)).thenReturn("ActiveCase");
            assertEquals("ActiveCase",JdbcMetadataScope.schema(connection,config,null));
            verify(statement).setMaxRows(1);verify(statement).setQueryTimeout(10);verify(statement,times(1)).executeQuery();verify(statement).close();verify(rows).close();
        }
    }
    @Test void ownerFallbackPreservesQuotedNamesAndNeverUsesOracleServiceOrCatalog()throws Exception {
        var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);when(metadata.getDatabaseProductName()).thenReturn("OceanBase");
        when(connection.prepareStatement(anyString())).thenThrow(new SQLFeatureNotSupportedException());
        when(metadata.getUserName()).thenThrow(new AbstractMethodError("getUserName"));
        assertEquals("MixedCase",JdbcMetadataScope.schema(connection,Map.of("dbType","ORACLE","username","\"MixedCase\"","database","SERVICE_NAME"),null));
        assertEquals("APP",JdbcMetadataScope.schema(connection,Map.of("dbType","OCEANBASE_ORACLE","username","app@tenant#cluster"),null));
        assertNull(JdbcMetadataScope.schema(connection,Map.of("dbType","ORACLE","database","SERVICE_NAME","jdbcURL","jdbc:oracle:thin:@host:1521:SERVICE_NAME"),null));
        verify(connection,never()).getCatalog();
    }
    @Test void dmOwnerFallbackAndUnknownDatabaseDoNotGuessAnotherSchema()throws Exception {
        var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getUserName()).thenReturn("REGISTERED");
        assertEquals("REGISTERED",JdbcMetadataScope.schema(connection,Map.of("dbType","dm"),null));
        assertNull(JdbcMetadataScope.schema(connection,Map.of("dbType","unknown","username","admin"),null));
        verify(connection,never()).prepareStatement(anyString());
    }
    @Test void disconnectionsAndTimeoutsCannotBeHiddenByLoginOwnerFallback()throws Exception {
        for(SQLException error:List.of(new SQLException("disconnected","08006"),new SQLTimeoutException("timeout"))) {
            var connection=mock(Connection.class);when(connection.getSchema()).thenThrow(error);
            assertSame(error,assertThrows(SQLException.class,()->JdbcMetadataScope.schema(connection,Map.of("dbType","oracle","username","LOGIN"),null)));
            verify(connection,never()).prepareStatement(anyString());
        }
    }
}
