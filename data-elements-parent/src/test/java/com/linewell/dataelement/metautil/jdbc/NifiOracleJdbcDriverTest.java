package com.linewell.dataelement.metautil.jdbc;

import org.junit.jupiter.api.Test;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NifiOracleJdbcDriverTest {
    @Test void blobStreamOverloadsKeepEmptyBytesDistinctFromNullWithoutChangingArrayBindings() throws Exception {
        var nativeStatement=mock(PreparedStatement.class);var statement=fixture(nativeStatement).prepareStatement("validation");
        var nativeConnection=mock(Connection.class);var empty=mock(Blob.class);
        when(nativeStatement.getConnection()).thenReturn(nativeConnection);when(nativeConnection.createBlob()).thenReturn(empty);
        statement.setBlob(1,new java.io.ByteArrayInputStream(new byte[0]));
        statement.setBlob(2,new java.io.ByteArrayInputStream(new byte[]{9}),0L);
        verify(nativeStatement).setBlob(1,empty);verify(nativeStatement).setBlob(2,empty);
        statement.setObject(7,new java.io.ByteArrayInputStream(new byte[0]),Types.BLOB);verify(nativeStatement).setBlob(7,empty);
        statement.setBlob(3,(java.io.InputStream)null);verify(nativeStatement).setBlob(3,(java.io.InputStream)null);
        byte[] expected={-128,-1,0,1,127};statement.setBlob(4,new java.io.ByteArrayInputStream(expected));
        var stream=org.mockito.ArgumentCaptor.forClass(java.io.InputStream.class);
        verify(nativeStatement).setBlob(eq(4),stream.capture());assertArrayEquals(expected,stream.getValue().readAllBytes());
        var array=new Object[]{1,2};statement.setObject(5,array,Types.ARRAY);verify(nativeStatement).setObject(5,array,Types.ARRAY);
        var broken=new java.io.InputStream(){@Override public int read()throws java.io.IOException{throw new java.io.IOException("validation-secret");}};
        var error=assertThrows(SQLDataException.class,()->statement.setBlob(6,broken));assertNull(error.getCause());assertFalse(error.getMessage().contains("validation-secret"));
    }
    @Test void blobsUseBinaryLobStreamsAndPreserveEveryByteAcrossNativeJsonCarriers() throws Exception {
        var nativeStatement=mock(PreparedStatement.class);var statement=fixture(nativeStatement).prepareStatement("validation");
        byte[] expected=new byte[12_000];new java.util.Random(7).nextBytes(expected);
        var signed=new java.util.ArrayList<Integer>();for(byte b:expected)signed.add((int)b);
        var unsigned=signed.stream().map(b->b&255).toList();
        var buffer=java.nio.ByteBuffer.allocate(expected.length+2);buffer.put((byte)1).put(expected).put((byte)2).flip();buffer.position(1);buffer.limit(expected.length+1);
        for(Object value:List.of(expected,buffer,signed,unsigned,signed.toArray(),java.util.Base64.getEncoder().encodeToString(expected))) {
            statement.setObject(1,value,Types.BLOB);
            var stream=org.mockito.ArgumentCaptor.forClass(java.io.InputStream.class);
            verify(nativeStatement).setBlob(eq(1),stream.capture(),eq((long)expected.length));
            assertArrayEquals(expected,stream.getValue().readAllBytes());clearInvocations(nativeStatement);
        }
        assertEquals(1,buffer.position());assertEquals(expected.length+1,buffer.limit());
        var blobConnection=mock(Connection.class);var emptyBlob=mock(Blob.class);
        when(nativeStatement.getConnection()).thenReturn(blobConnection);when(blobConnection.createBlob()).thenReturn(emptyBlob);
        statement.setObject(2,new byte[0],JDBCType.BLOB);verify(nativeStatement).setBlob(2,emptyBlob);
        statement.setObject(3,null,Types.BLOB);verify(nativeStatement).setBlob(3,(java.io.InputStream)null);
        var blob=mock(Blob.class);statement.setObject(4,blob,Types.BLOB);verify(nativeStatement).setBlob(4,blob);
        verify(nativeStatement,never()).setObject(anyInt(),any(),eq(Types.BLOB));
        statement.close();verify(emptyBlob).free();
    }
    @Test void invalidBlobCarriersAreRetainedWithoutStringificationOrValueDisclosure() throws Exception {
        var nativeStatement=mock(PreparedStatement.class);var statement=fixture(nativeStatement).prepareStatement("validation");
        for(Object invalid:List.of(List.of(256),List.of(-129),List.of(1.5),java.util.Arrays.asList(1,null),List.of("validation-secret"),"validation-invalid-base64",Map.of("value","validation-secret"))) {
            var error=assertThrows(SQLDataException.class,()->statement.setObject(1,invalid,Types.BLOB));
            assertEquals("22018",error.getSQLState());assertNull(error.getCause());assertFalse(error.getMessage().contains("validation-secret"));
        }
        verify(nativeStatement,never()).setBlob(anyInt(),any(java.io.InputStream.class),anyLong());
    }
    @Test void compatibleProtocolCannotSelectTheUnwrappedDriverOrExposeDetails() throws Exception {
        var driver = new NifiOracleJdbcDriver();
        assertTrue(driver.acceptsURL("jdbc:haitong:oracle:thin:@validation.invalid:1521/validation"));
        assertFalse(driver.acceptsURL("jdbc:oracle:thin:@validation.invalid:1521/validation"));
        assertFalse(driver.acceptsURL(null));
        assertNull(driver.connect("jdbc:oracle:thin:@validation.invalid:1521/validation", null));
        assertEquals(0, driver.getPropertyInfo("jdbc:postgresql://validation.invalid/validation", null).length);
        assertInstanceOf(NifiOracleJdbcDriver.class, DriverManager.getDriver("jdbc:haitong:oracle:thin:@validation.invalid/validation"));
    }

    @Test void binaryVendorCodesUseNativeDatumsWithFullFiniteRangeAndTypedNulls() throws Exception {
        var nativeStatement = mock(PreparedStatement.class);
        var connection = fixture(nativeStatement);
        var statement = connection.prepareStatement("validation");
        for (double value : new double[]{1.2345678901234567, Double.MAX_VALUE, Double.MIN_VALUE}) {
            statement.setObject(1, value, 101);
            var datum = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(nativeStatement).setObject(eq(1), datum.capture());
            assertEquals("oracle.sql.BINARY_DOUBLE", datum.getValue().getClass().getName());
            assertEquals(value, datum.getValue().getClass().getMethod("doubleValue").invoke(datum.getValue()));
            clearInvocations(nativeStatement);
        }
        statement.setObject(2, Float.MAX_VALUE, 100);
        var single = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(nativeStatement).setObject(eq(2), single.capture());
        assertEquals("oracle.sql.BINARY_FLOAT", single.getValue().getClass().getName());
        assertEquals(Float.MAX_VALUE, single.getValue().getClass().getMethod("floatValue").invoke(single.getValue()));
        statement.setObject(3, null, 101); verify(nativeStatement).setNull(3, Types.DOUBLE);
        statement.setObject(4, null, 100); verify(nativeStatement).setNull(4, Types.REAL);
        statement.setObject(5, 12.5, 101, 0);
        verify(nativeStatement).setObject(eq(5), argThat(value -> value.getClass().getName().equals("oracle.sql.BINARY_DOUBLE")));
        verify(nativeStatement, never()).setObject(anyInt(), any(), eq(101));
        statement.close(); connection.close();
    }

    @Test void invalidOrOutOfRangeBinaryValuesFailWithoutRenderingTheInput() throws Exception {
        var nativeStatement = mock(PreparedStatement.class);
        var statement = fixture(nativeStatement).prepareStatement("validation");
        for (Object value : List.of(Double.NaN, Double.POSITIVE_INFINITY, "1e309", Map.of("validation", "opaque"))) {
            assertEquals("22003", assertThrows(SQLDataException.class, () -> statement.setObject(1, value, 101)).getSQLState());
        }
        var malformed = assertThrows(SQLDataException.class, () -> statement.setObject(1, "validation-invalid-number", 101));
        assertEquals("22018", malformed.getSQLState());
        assertFalse(malformed.getMessage().contains("validation-invalid-number"));
        assertNull(malformed.getCause());
        for (double value : new double[]{Double.MAX_VALUE, Double.MIN_VALUE}) {
            assertEquals("22003", assertThrows(SQLDataException.class, () -> statement.setObject(1, value, 100)).getSQLState());
        }
        verify(nativeStatement, never()).setObject(anyInt(), any());
    }

    @Test void nationalLobsUseCharacterLobBindingsWithoutTruncationAndKeepNull() throws Exception {
        var nativeStatement = mock(PreparedStatement.class);
        var statement = fixture(nativeStatement).prepareStatement("validation");
        String text = "中文-😺-" + "x".repeat(5000);
        statement.setObject(1, text, Types.NCLOB);
        var reader = org.mockito.ArgumentCaptor.forClass(Reader.class);
        verify(nativeStatement).setNClob(eq(1), reader.capture());
        StringBuilder read = new StringBuilder(); char[] buffer = new char[512]; int count;
        while ((count = reader.getValue().read(buffer)) != -1) read.append(buffer, 0, count);
        assertEquals(text, read.toString());
        statement.setObject(2, null, JDBCType.NCLOB);
        verify(nativeStatement).setNClob(2, (Reader) null);
        var clob = mock(NClob.class); statement.setObject(3, clob, Types.NCLOB); verify(nativeStatement).setNClob(3, clob);
        assertEquals("22018", assertThrows(SQLDataException.class, () -> statement.setObject(4, new byte[]{1,2}, Types.NCLOB)).getSQLState());
        verify(nativeStatement, never()).setObject(anyInt(), any(), eq(Types.NCLOB));
    }

    @Test void ordinaryBindingsTransactionsAndExceptionsAreDelegatedUnchanged() throws Exception {
        var nativeConnection = mock(Connection.class); var nativeStatement = mock(PreparedStatement.class);
        when(nativeConnection.prepareStatement("validation")).thenReturn(nativeStatement);
        var connection = NifiOracleJdbcDriver.wrap(nativeConnection, getClass().getClassLoader());
        var statement = connection.prepareStatement("validation");
        var decimal = new BigDecimal("12345678901234567890.123456789");
        statement.setObject(1, decimal, Types.DECIMAL, 9); verify(nativeStatement).setObject(1, decimal, Types.DECIMAL, 9);
        connection.setAutoCommit(false); connection.commit(); connection.rollback();
        verify(nativeConnection).setAutoCommit(false); verify(nativeConnection).commit(); verify(nativeConnection).rollback();
        var failure = new SQLTransactionRollbackException("validation database failure", "40001");
        when(nativeStatement.executeUpdate()).thenThrow(failure);
        assertSame(failure, assertThrows(SQLTransactionRollbackException.class, statement::executeUpdate));
        statement.close(); connection.close(); verify(nativeStatement).close(); verify(nativeConnection).close();
    }

    @Test void metadataRemainsNativeAndConnectionAndOpenStatementIdentityIsStable() throws Exception {
        var nativeConnection = mock(Connection.class); var nativeStatement = mock(PreparedStatement.class);
        var metadata = mock(DatabaseMetaData.class); var rows = mock(ResultSet.class); var columns = mock(ResultSetMetaData.class);
        when(nativeConnection.prepareStatement("validation")).thenReturn(nativeStatement);
        when(nativeConnection.getMetaData()).thenReturn(metadata); when(metadata.getConnection()).thenReturn(nativeConnection);
        when(nativeStatement.getConnection()).thenReturn(nativeConnection); when(nativeStatement.executeQuery()).thenReturn(rows);
        when(rows.getStatement()).thenReturn(nativeStatement); when(rows.getMetaData()).thenReturn(columns);
        when(columns.getColumnType(1)).thenReturn(101);
        var connection = NifiOracleJdbcDriver.wrap(nativeConnection, getClass().getClassLoader());
        var statement = connection.prepareStatement("validation"); var result = statement.executeQuery();
        assertSame(connection, connection.getMetaData().getConnection()); assertSame(connection, statement.getConnection());
        assertSame(statement, result.getStatement()); assertSame(columns, result.getMetaData());
        assertEquals(101, result.getMetaData().getColumnType(1));
        assertSame(connection, connection.unwrap(Connection.class)); assertTrue(connection.isWrapperFor(Connection.class));
        assertFalse(statement.toString().contains("SELECT"));
        result.close(); statement.close(); connection.close();
    }

    @Test void pooledConnectionDoesNotRetainClosedStatementWrappersAcrossTwentyOrHundredUses() throws Exception {
        for (int count : List.of(20, 100)) {
            var nativeConnection = mock(Connection.class); var nativeStatement = mock(PreparedStatement.class);
            when(nativeConnection.prepareStatement("validation")).thenReturn(nativeStatement);
            var connection = NifiOracleJdbcDriver.wrap(nativeConnection, getClass().getClassLoader());
            PreparedStatement previous = null;
            for (int i = 0; i < count; i++) {
                var statement = connection.prepareStatement("validation");
                if (previous != null) assertNotSame(previous, statement);
                statement.close(); previous = statement;
            }
            verify(nativeStatement, times(count)).close(); connection.close();
        }
    }

    private Connection fixture(PreparedStatement statement) throws SQLException {
        var connection = mock(Connection.class);
        when(connection.prepareStatement("validation")).thenReturn(statement);
        return NifiOracleJdbcDriver.wrap(connection, getClass().getClassLoader());
    }
}
