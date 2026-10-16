package com.linewell.dataelement.feature.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Evidence;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Selection;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** One selected flow only. Authorization is batch-based and precedes EVERY external read. */
@Component
public class NifiDiagnosticEvidence {
    private static final String RESOURCE = "MENU:2081000000000000002";
    private static final Set<String> SOURCE_KEYS = Set.of("registeredDatasourceId", "selectedDatabaseId", "sourceDbId", "targetDbId", "dbId", "datasourceId", "dataSourceId");
    private static final Set<String> TABLE_KEYS = Set.of("sourceTableId", "targetTableId", "registeredTableId");
    private final PipelineRepository pipelines;
    private final ErrorService errors;
    private final JdbcTemplate jdbc;
    private final DataScopeAuthorizationService scopes;
    private final NifiClient nifi;
    private final ObjectMapper json;
    public NifiDiagnosticEvidence(PipelineRepository pipelines, ErrorService errors, JdbcTemplate jdbc,
                                  DataScopeAuthorizationService scopes, NifiClient nifi, ObjectMapper json) {
        this.pipelines = pipelines; this.errors = errors; this.jdbc = jdbc; this.scopes = scopes; this.nifi = nifi; this.json = json;
    }

    public record Authorized(Pipeline pipeline, FlowError error) {}
    public Authorized authorize(Selection selection) {
        String tenant = TenantContext.requireTenantId();
        if (TenantContext.usesControlDatabase()) throw denied();
        Pipeline pipeline = pipelines.findById(required(selection.pipelineId())).orElseThrow(NifiDiagnosticEvidence::denied);
        Set<String> sourceIds = new LinkedHashSet<>(), tableIds = new LinkedHashSet<>();
        List<Pipeline.Node> nodes = pipeline.dsl() == null || pipeline.dsl().nodes() == null ? List.of() : pipeline.dsl().nodes();
        if (nodes.size() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本次诊断最多支持100个画布节点，请选择较小流程");
        for (Pipeline.Node node : nodes) {
            if (node == null || node.config() == null) continue;
            SOURCE_KEYS.forEach(key -> add(sourceIds, node.config().get(key)));
            TABLE_KEYS.forEach(key -> add(tableIds, node.config().get(key)));
        }
        if (sourceIds.size() > 100 || tableIds.size() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联资源超过100项");
        if (!tableIds.isEmpty()) {
            List<Map<String, Object>> tables = rows("select tid, datasource_id from db_table_t where tenant_id=? and is_del=0 and tid in (" + placeholders(tableIds.size()) + ")", tenant, tableIds);
            Set<String> found = new HashSet<>();
            for (Map<String, Object> table : tables) { found.add(String.valueOf(table.get("tid"))); add(sourceIds, table.get("datasource_id")); }
            if (!found.equals(tableIds)) throw denied();
        }
        if (sourceIds.isEmpty() || sourceIds.size() > 100) throw denied();
        List<Map<String, Object>> sources = rows("select tid, created_by, org_id from db_datasource_t where tenant_id=? and is_del=0 and tid in (" + placeholders(sourceIds.size()) + ")", tenant, sourceIds);
        var decision = scopes.currentScope(RESOURCE);
        Set<String> visible = new HashSet<>();
        for (Map<String, Object> source : sources) if (scopes.canRead(source, decision)) visible.add(String.valueOf(source.get("tid")));
        if (!visible.equals(sourceIds)) throw denied();
        FlowError error = errors.list(pipeline.id()).stream().filter(e -> Objects.equals(e.id(), required(selection.errorId())))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "错误记录已过期或已清空，请刷新后重试"));
        return new Authorized(pipeline, error);
    }

    public List<Evidence> collect(Authorized authorized) {
        Pipeline p = authorized.pipeline(); FlowError e = authorized.error();
        List<Evidence> result = new ArrayList<>();
        result.add(new Evidence("error", "NiFi历史错误（发生时）", e.occurredAt(), DiagnosticSanitizer.errorText(
                "阶段: " + e.phase() + "\n节点: " + e.nodeLabel() + "\n字段: " + e.fieldKey() + "\n错误: " + e.message() + "\n异常链: " + Objects.toString(e.detail(), ""))));
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (p.dsl() != null && p.dsl().nodes() != null) for (Pipeline.Node node : p.dsl().nodes()) {
            if (node == null) continue;
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("node", node.id()); summary.put("label", node.label()); summary.put("type", node.manifestKey()); summary.put("category", node.category());
            if (node.config() != null) for (String key : List.of("dbType", "sourceDbType", "targetDbType", "syncMode", "batchSize", "schedulingPeriod")) {
                Object value = node.config().get(key);
                if (value instanceof String || value instanceof Number || value instanceof Boolean) summary.put(key, value);
            }
            nodes.add(summary);
        }
        result.add(evidence("design", "已保存画布与部署版本（采集时）", Map.of("name", Objects.toString(p.name(), ""),
                "status", p.status(), "dslVersion", p.dsl() == null ? 0 : p.dsl().version(), "lastDeployedAt", Objects.toString(p.lastDeployedAt(), "未部署"),
                "deploymentVersion", Objects.toString(p.lastDeployedHash(), "未部署"), "nodes", nodes)));
        String pg = p.nifiProcessGroupId();
        if (pg == null || pg.isBlank()) { result.add(evidence("missing", "取证说明", "流程尚未部署，无实时NiFi证据")); return result; }
        Set<String> processors = p.nodeMapping() == null || p.nodeMapping().processorIdToCanvasNode() == null ? Set.of() : p.nodeMapping().processorIdToCanvasNode().keySet();
        // Four business reads at most; no node/row-triggered follow-up requests.
        attempt(result, "bulletins", "NiFi流程公告（采集时，最多20条）", () -> {
            List<Object> bulletins = new ArrayList<>();
            for (JsonNode row : nifi.getBulletinBoard(pg, 0L).path("bulletinBoard").path("bulletins")) {
                JsonNode bulletin = row.path("bulletin");
                if (!pg.equals(bulletin.path("groupId").asText()) && !processors.contains(bulletin.path("sourceId").asText())) continue;
                bulletins.add(pick(bulletin, "level", "message", "sourceName", "timestamp"));
                if (bulletins.size() == 20) break;
            }
            return bulletins;
        });
        attempt(result, "status", "NiFi流程状态与队列（采集时）", () -> {
            JsonNode aggregate = nifi.getProcessGroupStatus(pg).path("processGroupStatus").path("aggregateSnapshot");
            if (!pg.equals(aggregate.path("id").asText())) throw new IllegalStateException("无法确认流程归属");
            return pick(aggregate, "id", "activeThreadCount", "queuedCount", "flowFilesQueued", "bytesQueued", "input", "output", "read", "written", "processingNanos");
        });
        String processor = e.nodeId() == null || p.nodeMapping() == null || p.nodeMapping().primaryProcessorIds() == null ? null : p.nodeMapping().primaryProcessorIds().get(e.nodeId());
        if (processor != null && processors.contains(processor)) attempt(result, "processor", "选中处理器（采集时）", () -> {
            JsonNode component = nifi.getProcessor(processor).path("component");
            if (!pg.equals(component.path("parentGroupId").asText())) throw new IllegalStateException("处理器不属于当前流程组");
            Map<String, Object> view = pick(component, "name", "type", "state", "validationErrors", "validationStatus");
            view.put("bundle", pick(component.path("bundle"), "group", "artifact", "version")); return view;
        });
        attempt(result, "services", "本流程组控制器服务（采集时，最多100项）", () -> {
            List<Object> services = new ArrayList<>();
            for (JsonNode row : nifi.listControllerServices(pg)) {
                JsonNode component = row.path("component");
                if (!pg.equals(component.path("parentGroupId").asText())) continue;
                if (services.size() >= 100) throw new IllegalStateException("控制器服务超限");
                services.add(pick(component, "name", "type", "state", "validationErrors", "validationStatus"));
            }
            return services;
        });
        return result;
    }
    private void attempt(List<Evidence> target, String id, String source, Supplier<Object> load) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        try { target.add(evidence(id, source, load.get())); }
        catch (RuntimeException unavailable) { target.add(evidence(id, source, "未取得该项证据：服务不可用、超限或无法核验归属；不可据此判断正常")); }
    }
    private Evidence evidence(String id, String source, Object content) {
        try {
            String text = DiagnosticSanitizer.text(json.writeValueAsString(content));
            if (text.length() > 8000) text = text.substring(0, 8000) + "\n[该项证据超限，已截取]";
            return new Evidence(id, source, Instant.now(), text);
        } catch (Exception invalid) { throw new IllegalStateException("证据格式无效"); }
    }
    private Map<String, Object> pick(JsonNode value, String... keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) if (value.hasNonNull(key)) {
            JsonNode item = value.get(key);
            if (item.isTextual()) result.put(key, DiagnosticSanitizer.errorText(item.asText()));
            else if (item.isNumber() || item.isBoolean()) result.put(key, json.convertValue(item, Object.class));
            else if (item.isArray()) {
                List<String> messages = new ArrayList<>();
                for (int i = 0; i < Math.min(20, item.size()); i++) messages.add(item.get(i).isTextual()
                        ? DiagnosticSanitizer.errorText(item.get(i).asText()) : "[结构化原文未展示]");
                if (item.size() > 20) messages.add("[该字段仅展示前20项]");
                result.put(key, messages);
            }
        }
        return result;
    }
    private List<Map<String, Object>> rows(String sql, String tenant, Set<String> ids) {
        List<Object> args = new ArrayList<>(); args.add(tenant); args.addAll(ids);
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql); statement.setMaxRows(101); statement.setQueryTimeout(5);
            for (int i = 0; i < args.size(); i++) statement.setObject(i + 1, args.get(i));
            return statement;
        }, new org.springframework.jdbc.core.ColumnMapRowMapper());
    }
    private static String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }
    private static void add(Set<String> ids, Object value) { if (value != null && !String.valueOf(value).isBlank()) ids.add(required(String.valueOf(value))); }
    private static String required(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,100}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "流程或错误标识无效");
        return value;
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "流程或关联资源不存在、归属不明或无访问权限"); }
}
