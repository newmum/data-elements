package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
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
    private static final int DM_LOCK_ID = LOCK_NAME.hashCode() & 0x3fffffff;
    private final DataSource controlDataSource;
    private com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService runtimeStatus;
    @org.springframework.beans.factory.annotation.Autowired
    public void setRuntimeStatus(com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService service) { runtimeStatus = service; }

    public NifiMutationAspect(@Qualifier("controlDataSource") DataSource controlDataSource) {
        this.controlDataSource = controlDataSource;
    }

    @Around("@annotation(com.linewell.dataelement.integration.nifi.canvas.lifecycle.SerializedNifiMutation)")
    public Object serialize(ProceedingJoinPoint invocation) throws Throwable {
        TenantContext.requireTenantId();
        try (Connection connection = controlDataSource.getConnection()) {
            boolean dameng = connection.getMetaData().getURL().toLowerCase(Locale.ROOT).startsWith("jdbc:dm:");
            if (!acquire(connection, dameng)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "正在保存或部署流程，请等待完成后重试");
            }
            try {
                return invocation.proceed();
            } finally {
                try {
                    if (runtimeStatus != null) runtimeStatus.invalidateTenant();
                } finally {
                    if (dameng) executeLock(connection, "SELECT SYS.DBMS_LOCK.RELEASE(?) FROM DUAL", DM_LOCK_ID);
                    else executeLock(connection, "SELECT RELEASE_LOCK(?)", LOCK_NAME);
                }
            }
        }
    }

    private boolean acquire(Connection connection, boolean dameng) throws Exception {
        if (!dameng) return executeLock(connection, "SELECT GET_LOCK(?, 0)", LOCK_NAME) == 1;
        // Mode 6 is an exclusive session lock. DM returns 0 for success and 1 for timeout.
        int result = executeLock(connection, "SELECT SYS.DBMS_LOCK.REQUEST(?, 6, 0) FROM DUAL", DM_LOCK_ID);
        if (result == 0) return true;
        if (result == 1) return false;
        throw new IllegalStateException("达梦流程互斥锁申请失败，返回码：" + result);
    }

    private int executeLock(Connection connection, String sql, Object key) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return -1;
                int status = result.getInt(1);
                return result.wasNull() ? -1 : status;
            }
        }
    }
}
