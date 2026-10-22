package com.linewell.dataelement.dataservice.flowserve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Read the existing service DSL through the same object scope as the service-management list. */
@Service
public class FlowServeCanvasReadService {
    private final Function<String, JdbcTemplate> database;
    private final DataScopeAuthorizationService authorization;
    private final ObjectMapper mapper;

    @Autowired
    public FlowServeCanvasReadService(TenantDataSourceRegistry databases,
            DataScopeAuthorizationService authorization, ObjectMapper mapper) {
        this(tenant -> new JdbcTemplate(databases.dataSourceForTenant(tenant)), authorization, mapper);
    }

    FlowServeCanvasReadService(Function<String, JdbcTemplate> database,
            DataScopeAuthorizationService authorization, ObjectMapper mapper) {
        this.database = database;
        this.authorization = authorization;
        this.mapper = mapper;
    }

    public Map<String, Object> flow(String id) {
        String tenant = TenantContext.requireTenantId();
        if (id == null || id.isBlank() || id.length() > 64) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "流程标识无效");
        var scope = authorization.currentScope(DataScopeAuthorizationService.DATA_SOURCE_RESOURCE);
        List<Object> arguments = new ArrayList<>(List.of(id, tenant));
        String scopeSql = switch (scope.scope()) {
            case ALL -> "";
            case OWNER -> { arguments.add(scope.userId()); yield " AND a.created_by=?"; }
            case ORG -> {
                if (scope.organizationIds().isEmpty()) yield " AND 1=0";
                if (scope.organizationIds().size() > 1000) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "部门数据范围超出查询上限，请联系系统维护人员");
                arguments.addAll(scope.organizationIds());
                yield " AND a.org_id IN (" + String.join(",", java.util.Collections.nCopies(scope.organizationIds().size(), "?")) + ")";
            }
        };
        var rows = database.apply(tenant).queryForList("""
                SELECT f.id,f.dsl,f.status,f.lock_version,f.updated_at
                  FROM fs_flow f
                 WHERE f.id=? AND f.tenant_id=? AND f.deleted=0
                   AND EXISTS (SELECT 1 FROM api_info_t a
                        WHERE a.tenant_id=f.tenant_id AND a.is_del=0
                          AND (a.flow_id=f.id OR a.tid=f.id)
                """ + scopeSql + ")", arguments.toArray());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "服务流程不存在或不在当前数据范围内");
        var row = rows.getFirst();
        try {
            var flow = mapper.readTree(String.valueOf(row.get("dsl")));
            if (!flow.isObject() || !id.equals(flow.path("flowId").asText()))
                throw new IllegalArgumentException("流程标识与编排内容不一致");
            return Map.of("flow", flow, "status", row.get("status"), "lockVersion", row.get("lock_version"),
                    "updatedAt", row.get("updated_at").toString());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务编排内容无效，请检查该服务的已保存流程", exception);
        }
    }

    public List<Map<String, Object>> runtimeNodes() {
        String tenant = TenantContext.requireTenantId();
        JdbcTemplate jdbc = database.apply(tenant);
        // Runtime node choices are a bounded configuration list, with no per-node health or credential reads.
        jdbc.setMaxRows(101);
        var nodes = jdbc.queryForList("""
                SELECT id,name,business_domain AS businessDomain,service_type AS serviceType,
                       base_url AS baseUrl,management_url AS managementUrl,
                       CASE WHEN deploy_token IS NULL OR deploy_token='' THEN 0 ELSE 1 END AS deployTokenSet,
                       local_node AS localNode,default_node AS defaultNode,enabled,created_at AS createdAt
                  FROM fs_runtime_node WHERE tenant_id=? ORDER BY default_node DESC,name,id
                """, tenant);
        if (nodes.size() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前租户运行节点超过100项，请先整理运行节点配置");
        // SQL booleans differ between drivers; the frontend contract is always true/false.
        for (var node : nodes) for (String key : List.of("deployTokenSet", "localNode", "defaultNode", "enabled")) {
            Object value = node.get(key);
            node.put(key, Boolean.TRUE.equals(value) || "1".equals(String.valueOf(value)));
        }
        return nodes;
    }
}
