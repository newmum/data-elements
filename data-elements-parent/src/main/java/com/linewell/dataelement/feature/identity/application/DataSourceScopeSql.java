package com.linewell.dataelement.feature.identity.application;

import java.util.Collections;
import java.util.List;

/** Apply one resolved scope to the entire SQL read, including counts and all requested IDs. */
public final class DataSourceScopeSql {
    private DataSourceScopeSql() {}

    public static String predicate(DataScopeAuthorizationService service, String alias, List<Object> args) {
        if (service == null) throw new IllegalStateException("数据范围服务尚未就绪");
        var scope = service.currentScope(DataScopeAuthorizationService.DATA_SOURCE_RESOURCE);
        return switch (scope.scope()) {
            case ALL -> "1=1";
            case OWNER -> { args.add(scope.userId()); yield alias + ".created_by=?"; }
            case ORG -> {
                if (scope.organizationIds().isEmpty()) yield "1=0";
                args.addAll(scope.organizationIds());
                yield alias + ".org_id IN (" + String.join(",", Collections.nCopies(scope.organizationIds().size(), "?")) + ")";
            }
        };
    }
}
