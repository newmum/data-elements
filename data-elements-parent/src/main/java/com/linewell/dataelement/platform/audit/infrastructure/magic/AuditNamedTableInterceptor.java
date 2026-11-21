package com.linewell.dataelement.platform.audit.infrastructure.magic;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpUtil;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.modules.db.inteceptor.NamedTableInterceptor;
import org.ssssssss.magicapi.modules.db.model.SqlMode;
import org.ssssssss.magicapi.modules.db.table.NamedTable;

/**
 * Applies actor and timestamp auditing to every Magic named-table write whose
 * physical table declares the matching audit columns.
 */
@Component
public class AuditNamedTableInterceptor implements NamedTableInterceptor {

    private static final Set<String> CALLER_AUDITED_TABLES = Set.of(
            "iam_audit_event_t", "log_user_login_t", "log_system_t", "log_request_t", "log_batch_item_t",
            "log_audit_outbox_t", "log_export_job_t", "log_archive_batch_t");

    private final AuditTableRegistry tableRegistry;

    public AuditNamedTableInterceptor(AuditTableRegistry tableRegistry) {
        this.tableRegistry = tableRegistry;
    }

    @Override
    public void preHandle(SqlMode sqlMode, NamedTable namedTable) {
        if (sqlMode != SqlMode.INSERT && sqlMode != SqlMode.UPDATE) {
            return;
        }
        if (CALLER_AUDITED_TABLES.contains(namedTable.getTableName().toLowerCase(Locale.ROOT))) {
            // Audit events and log jobs carry their own actor and UTC timestamps.
            return;
        }
        AuditTableRegistry.AuditColumns columns = tableRegistry.columnsOf(namedTable.getTableName());
        if (columns.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String actor = currentActor();
        if (sqlMode == SqlMode.INSERT) {
            set(namedTable, columns.createdTime(), now);
            set(namedTable, columns.createdBy(), actor);
        }
        // A newly created row is also its first revision, so both sides of the
        // audit pair are initialized on INSERT and only the update pair changes
        // on later writes.
        set(namedTable, columns.updatedTime(), now);
        set(namedTable, columns.updatedBy(), actor);
    }

    private String currentActor() {
        Object loginId;
        try {
            loginId = StpUtil.getLoginIdDefaultNull();
        } catch (SaTokenContextException unavailable) {
            // Background writes still receive audit timestamps when no request session is bound.
            return null;
        }
        if (loginId == null) {
            return null;
        }
        String value = String.valueOf(loginId).trim();
        return value.isEmpty() ? null : value;
    }

    private void set(NamedTable namedTable, String column, Object value) {
        if (column != null && value != null) {
            namedTable.column(column, value);
        }
    }
}
