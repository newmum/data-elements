package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NifiMutationAspectTest {
    @Test
    void concurrentRequestIsRejectedBeforeItCanReadOrCreateAnyGroups() throws Throwable {
        try (var scope = TenantContext.use("tenant")) {
            DataSource source = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet result = mock(ResultSet.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(result);
            when(result.next()).thenReturn(true);
            when(result.getInt(1)).thenReturn(0);
            ProceedingJoinPoint work = mock(ProceedingJoinPoint.class);
            assertThatThrownBy(() -> new NifiMutationAspect(source).serialize(work))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
            verifyNoInteractions(work);
            verify(connection).close();
        }
    }

    @Test
    void failureReleasesLockOnTheSameConnection() throws Throwable {
        try (var scope = TenantContext.use("tenant")) {
            DataSource source = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet result = mock(ResultSet.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(result);
            when(result.next()).thenReturn(true);
            when(result.getInt(1)).thenReturn(1);
            ProceedingJoinPoint work = mock(ProceedingJoinPoint.class);
            when(work.proceed()).thenThrow(new IllegalStateException("deployment failed"));
            assertThatThrownBy(() -> new NifiMutationAspect(source).serialize(work)).hasMessage("deployment failed");
            var order = inOrder(connection, work);
            order.verify(connection).prepareStatement("SELECT GET_LOCK(?, 0)");
            order.verify(work).proceed();
            order.verify(connection).prepareStatement("SELECT RELEASE_LOCK(?)");
            order.verify(connection).close();
        }
    }
}
