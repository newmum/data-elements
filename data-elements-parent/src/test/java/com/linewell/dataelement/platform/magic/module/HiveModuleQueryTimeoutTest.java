package com.linewell.dataelement.platform.magic.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.InOrder;

@ResourceLock("hive.query.table.timeout.seconds")
class HiveModuleQueryTimeoutTest {

    private static final String PROPERTY = "hive.query.table.timeout.seconds";
    private final String previousValue = System.getProperty(PROPERTY);

    @AfterEach
    void restoreProperty() {
        if (previousValue == null) System.clearProperty(PROPERTY);
        else System.setProperty(PROPERTY, previousValue);
    }

    @Test
    void queryTableSetsTenSecondTimeoutBeforeExecutingByDefault() throws Exception {
        System.clearProperty(PROPERTY);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement("select 1")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("id");
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn(42);
        HiveModule module = spy(new HiveModule());
        doReturn(connection).when(module).getConnection();

        assertThat(module.queryTable("select 1")).isEqualTo(List.of(Map.of("id", 42)));

        InOrder order = inOrder(statement);
        order.verify(statement).setQueryTimeout(10);
        order.verify(statement).executeQuery();
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void queryTableUsesPositiveServerOverride() throws Exception {
        System.setProperty(PROPERTY, "3");
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement("select 1")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(0);
        HiveModule module = spy(new HiveModule());
        doReturn(connection).when(module).getConnection();

        module.queryTable("select 1");

        verify(statement).setQueryTimeout(3);
    }

    @Test
    void showTablesUsesTheSameReadTimeoutBeforeExecuting() throws Exception {
        System.setProperty(PROPERTY, "3");
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement("SHOW TABLES IN analytics")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString(1)).thenReturn("events");
        HiveModule module = spy(new HiveModule());
        doReturn(connection).when(module).getConnection();

        assertThat(module.showTables("analytics")).containsExactly("events");

        InOrder order = inOrder(statement);
        order.verify(statement).setQueryTimeout(3);
        order.verify(statement).executeQuery();
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void ddlExecutionDoesNotInheritTheReadTimeout() throws Exception {
        System.setProperty(PROPERTY, "3");
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement("CREATE TABLE t (id INT)")).thenReturn(statement);
        HiveModule module = spy(new HiveModule());
        doReturn(connection).when(module).getConnection();

        module.execDDLSql("CREATE TABLE t (id INT)");

        verify(statement, never()).setQueryTimeout(anyInt());
        verify(statement).execute();
    }
}
