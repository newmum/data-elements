package com.linewell.dataelement.platform.audit.infrastructure.magic;

import cn.dev33.satoken.stp.StpUtil;
import java.time.LocalDateTime;
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

    private final AuditTableRegistry tableRegistry;

    public AuditNamedTableInterceptor(AuditTableRegistry tableRegistry) {
        this.tableRegistry = tableRegistry;
    }

    @Override
    public void preHandle(SqlMode sqlMode, NamedTable namedTable) {
        if (sqlMode != SqlMode.INSERT && sqlMode != SqlMode.UPDATE) {
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
        if (!StpUtil.isLogin()) {
            return null;
        }
        Object loginId = StpUtil.getLoginId();
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
