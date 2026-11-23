package com.linewell.dataelement.metautil.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NifiHiveJdbcDriverTest {
    @Test void uniqueProtocolCannotAccidentallySelectTheNativeHiveDriver() throws Exception {
        var driver = new NifiHiveJdbcDriver();
        assertTrue(driver.acceptsURL("jdbc:haitong:hive2://validation.invalid/default;auth=none"));
        assertFalse(driver.acceptsURL("jdbc:hive2://validation.invalid/default"));
        assertFalse(driver.acceptsURL(null));
        assertNull(driver.connect("jdbc:hive2://validation.invalid/default", null));
        assertEquals(0, driver.getPropertyInfo("jdbc:mysql://validation.invalid", null).length);
        assertInstanceOf(NifiHiveJdbcDriver.class, DriverManager.getDriver("jdbc:haitong:hive2://validation.invalid/default"));
    }

    @Test void onlyUnsupportedOptionalMetadataFallsBackWithoutRenamingFields() throws Exception {
        var metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnLabel(1)).thenReturn("source.original_name");
        when(metadata.getColumnName(1)).thenReturn("original_name");
        when(metadata.getColumnType(1)).thenReturn(Types.INTEGER);
        when(metadata.getColumnType(2)).thenReturn(Types.VARCHAR);
        for (int column = 1; column <= 2; column++) {
            when(metadata.getTableName(column)).thenThrow(new SQLFeatureNotSupportedException("Method not supported"));
            when(metadata.getSchemaName(column)).thenThrow(new SQLFeatureNotSupportedException("Method not supported"));
            when(metadata.getCatalogName(column)).thenThrow(new SQLFeatureNotSupportedException("Method not supported"));
            when(metadata.isSigned(column)).thenThrow(new SQLFeatureNotSupportedException("Method not supported"));
        }
        var compatible = fixture(metadata).metadata();
        assertEquals("", compatible.getTableName(1));
        assertEquals("", compatible.getSchemaName(1));
        assertEquals("", compatible.getCatalogName(1));
        assertTrue(compatible.isSigned(1));
        assertFalse(compatible.isSigned(2));
        assertEquals("source.original_name", compatible.getColumnLabel(1));
        assertEquals("original_name", compatible.getColumnName(1));
        assertEquals(Types.INTEGER, compatible.getColumnType(1));
    }

    @Test void otherDriverErrorsAndUnknownCapabilitiesRemainObservable() throws Exception {
        var metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(1);
        var original = new SQLException("validation database error", "28000", 100);
        when(metadata.getTableName(1)).thenThrow(original);
        when(metadata.getColumnClassName(1)).thenThrow(new SQLFeatureNotSupportedException("class unavailable"));
        when(metadata.unwrap(Runnable.class)).thenThrow(new SQLFeatureNotSupportedException("unwrap unavailable"));
        when(metadata.getColumnType(1)).thenReturn(Types.OTHER);
        when(metadata.isSigned(1)).thenThrow(new SQLFeatureNotSupportedException("sign unavailable"));
        var compatible = fixture(metadata).metadata();
        assertSame(original, assertThrows(SQLException.class, () -> compatible.getTableName(1)));
        assertThrows(SQLFeatureNotSupportedException.class, () -> compatible.getColumnClassName(1));
        assertThrows(SQLFeatureNotSupportedException.class, () -> compatible.unwrap(Runnable.class));
        assertThrows(SQLFeatureNotSupportedException.class, () -> compatible.isSigned(1));
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void metadataFallbackAddsNoPerFieldDatabaseQuery(int columns) throws Exception {
        var metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(columns);
        when(metadata.getTableName(anyInt())).thenThrow(new SQLFeatureNotSupportedException());
        when(metadata.isSigned(anyInt())).thenThrow(new SQLFeatureNotSupportedException());
        when(metadata.getColumnType(anyInt())).thenReturn(Types.INTEGER);
        var fixture = fixture(metadata);
        for (int i = 1; i <= columns; i++) {
            assertEquals("", fixture.metadata().getTableName(i));
            assertTrue(fixture.metadata().isSigned(i));
        }
        verify(fixture.connection(), times(1)).prepareStatement("validation query");
        verify(fixture.statement(), times(1)).executeQuery();
        verifyNoMoreInteractions(fixture.connection(), fixture.statement());
    }

    @Test void realJdbcValuesTransactionsUnwrapAndResourceClosureAreDelegated() throws Exception {
        var nativeConnection = DriverManager.getConnection("jdbc:h2:mem:hive-compat-validation;DB_CLOSE_DELAY=-1");
        var connection = NifiHiveJdbcDriver.wrap(nativeConnection);
        assertTrue(connection.getAutoCommit());
        connection.setAutoCommit(false);
        assertFalse(nativeConnection.getAutoCommit());
        try (var statement = connection.prepareStatement("SELECT CAST(1 AS INTEGER) AS N, CAST(NULL AS VARCHAR) AS T")) {
            assertSame(connection, statement.getConnection());
            try (var rows = statement.executeQuery()) {
                assertSame(statement, rows.getStatement());
                assertTrue(rows.next());
                assertEquals(1, rows.getInt("N"));
                assertNull(rows.getObject("T"));
                assertEquals(Types.INTEGER, rows.getMetaData().getColumnType(1));
                assertTrue(rows.getMetaData().isSigned(1));
            }
        }
        assertSame(connection, connection.unwrap(Connection.class));
        assertTrue(connection.isWrapperFor(Connection.class));
        connection.rollback();
        connection.close();
        assertTrue(nativeConnection.isClosed());
    }

    private record Fixture(Connection connection, PreparedStatement statement, ResultSetMetaData metadata) { }
    private Fixture fixture(ResultSetMetaData metadata) throws Exception {
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        when(connection.prepareStatement("validation query")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(metadata);
        var compatible = NifiHiveJdbcDriver.wrap(connection);
        return new Fixture(connection, statement, compatible.prepareStatement("validation query").executeQuery().getMetaData());
    }
}
