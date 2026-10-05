package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Aspect
@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 100)
public class NifiMutationAspect {
    // Tenants may share one NiFi root. A control-database advisory lock also protects
    // sibling position allocation and survives requests reaching different JVMs.
    private static final String LOCK_NAME = "data-elements:nifi:canvas-mutation:v1";
    private final DataSource controlDataSource;

    public NifiMutationAspect(@Qualifier("controlDataSource") DataSource controlDataSource) {
        this.controlDataSource = controlDataSource;
    }

    @Around("@annotation(com.linewell.dataelement.integration.nifi.canvas.lifecycle.SerializedNifiMutation)")
    public Object serialize(ProceedingJoinPoint invocation) throws Throwable {
        TenantContext.requireTenantId();
        try (Connection connection = controlDataSource.getConnection()) {
            if (!lock(connection, "SELECT GET_LOCK(?, 0)")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "正在保存或部署流程，请等待完成后重试");
            }
            try {
                return invocation.proceed();
            } finally {
                lock(connection, "SELECT RELEASE_LOCK(?)");
            }
        }
    }

    private boolean lock(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, LOCK_NAME);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }
}
