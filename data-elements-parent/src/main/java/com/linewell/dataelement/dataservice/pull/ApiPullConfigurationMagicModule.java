package com.linewell.dataelement.dataservice.pull;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * Tenant-scoped API pull configuration. It stores only credential references;
 * secret material is resolved by the server/NiFi runtime and is never accepted here.
 */
@Component
@MagicModule("apiPullConfig")
public class ApiPullConfigurationMagicModule {
    private static final Set<String> AUTH_TYPES = Set.of(
            "NONE", "API_KEY", "BEARER", "OAUTH2_CLIENT_CREDENTIALS", "JWT", "HMAC", "MTLS", "CUSTOM");
    private static final Set<String> TRIGGER_TYPES = Set.of("MANUAL", "CRON", "EVENT");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final TenantDataSourceRegistry tenants;

    public ApiPullConfigurationMagicModule(TenantDataSourceRegistry tenants) {
        this.tenants = tenants;
    }

    @Comment("校验当前租户 API 数据源并读取第一步保存的 API 定义，不做服务或规则写入")
    public List<Map<String, Object>> definitions(String datasourceId) {
        String tenantId = tenantId();
        requireId(datasourceId, "数据源标识不能为空");
        return apiPullItems(text(requireApiPullDatasource(tenantId, datasourceId).get("pool_cfg"), ""));
    }

    @Comment("校验 API 抓取登记定义及凭据边界")
    public boolean validateDefinitions(List<?> definitions) {
        if (definitions == null || definitions.isEmpty() || definitions.size() > 100) {
            throw new IllegalArgumentException("请登记 1 至 100 条 API");
        }
        Set<String> names = new java.util.HashSet<>();
        for (Object entry : definitions) {
            if (!(entry instanceof Map<?, ?> item)) throw new IllegalArgumentException("API 定义格式无效");
            String name = text(item.get("tableName"), "");
            if (!name.matches("[A-Za-z][A-Za-z0-9_]{0,95}") || !names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("API 数据资源英文名无效或重复");
            }
            if (text(item.get("serviceName"), "").isBlank() || text(item.get("tableComment"), "").isBlank()) {
                throw new IllegalArgumentException("请填写 API 服务名称和数据表中文名");
            }
            URI base = URI.create(text(item.get("baseUrl"), ""));
            String path = text(item.get("endpointPath"), "");
            if (!Set.of("http", "https").contains(text(base.getScheme(), "").toLowerCase(Locale.ROOT))
                    || base.getHost() == null || base.getUserInfo() != null || !path.startsWith("/") || path.startsWith("//")) {
                throw new IllegalArgumentException("请填写有效的 HTTP(S) API 地址和相对路径");
            }
            for (String field : List.of("authPassword", "authToken", "apiKeyValue", "oauthClientSecret")) {
                if (!text(item.get(field), "").isBlank()) throw new IllegalArgumentException("API 登记请使用凭据引用");
            }
            rejectSensitiveHeaders(text(item.get("commonHeadersJson"), ""));
            rejectSensitiveHeaders(text(item.get("requestHeadersJson"), ""));
            String auth = text(item.get("authType"), "NONE").toUpperCase(Locale.ROOT);
            if (!"NONE".equals(auth) && text(item.get("credentialRef"), "").isBlank()) {
                throw new IllegalArgumentException("启用 API 认证时请填写凭据引用");
            }
        }
        return true;
    }

    @Comment("把逻辑表英文名和服务名称规范化为可保存的 API 服务编码")
    public String suggestedServiceCode(String tableName, String serviceName) {
        requireId(tableName, "API 定义缺少数据表英文名");
        requireId(serviceName, "API 定义缺少接口服务名称");
        return serviceCode(tableName, serviceName);
    }

    @Comment("验证当前租户上下文；API 拉取表由租户数据库基线维护")
    public boolean prepareRuleSchema() {
        tenantId();
        return true;
    }

    @Comment("验证当前租户数据源确实为 API 拉取，并确保规则表已初始化")
    public boolean validateDatasource(String datasourceId) {
        String tenantId = tenantId();
        requireId(datasourceId, "数据源标识不能为空");
        assertApiPullDatasource(tenantId, datasourceId);
        return true;
    }

    @Comment("保存当前租户的数据源 API 服务配置；认证只允许 credentialRef")
    public Map<String, Object> saveService(Map<String, Object> body) {
        String tenantId = tenantId();
        String datasourceId = required(body, "datasourceId", "请选择数据源");
        assertApiPullDatasource(tenantId, datasourceId);
        String serviceName = required(body, "serviceName", "请填写接口服务名称");
        String serviceCode = required(body, "serviceCode", "请填写接口服务编码").toLowerCase(Locale.ROOT);
        if (!serviceCode.matches("[a-z][a-z0-9_-]{1,95}")) throw new IllegalArgumentException("接口服务编码只能使用小写字母、数字、-、_");
        String baseUrl = required(body, "baseUrl", "请填写 Base URL");
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException invalidUri) {
            throw new IllegalArgumentException("Base URL 格式不正确");
        }
        String scheme = text(uri.getScheme(), "").toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
            throw new IllegalArgumentException("接口服务 Base URL 必须以 http:// 或 https:// 开头，并包含主机名");
        }
        String authType = text(body.get("authType"), "NONE").toUpperCase(Locale.ROOT);
        if (!AUTH_TYPES.contains(authType)) throw new IllegalArgumentException("不支持的认证方式");
        String credentialRef = text(body.get("credentialRef"), "");
        if (!"NONE".equals(authType) && credentialRef.isBlank()) throw new IllegalArgumentException("认证方式启用后必须填写凭据引用");
        rejectSensitiveHeaders(text(body.get("commonHeadersJson"), ""));
        String id = text(body.get("tid"), "");
        LocalDateTime now = LocalDateTime.now();
        JdbcTemplate jdbc = jdbc(tenantId);
        if (id.isBlank()) {
            id = id();
            jdbc.update("""
                    insert into data_pull_service_t(tid,tenant_id,datasource_id,service_name,service_code,base_url,path_prefix,
                      protocol,service_type,media_type,network_mode,outbound_allowlist,connect_timeout_ms,read_timeout_ms,
                      max_response_bytes,tls_mode,client_cert_ref,common_headers_json,auth_type,credential_ref,auth_config_json,
                      status,created_by,created_time,updated_by,updated_time,is_del)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                    """, id, tenantId, datasourceId, serviceName, serviceCode, baseUrl, nullable(body, "pathPrefix"),
                    scheme.toUpperCase(Locale.ROOT), text(body.get("serviceType"), "REST"), text(body.get("mediaType"), "application/json"),
                    text(body.get("networkMode"), "DIRECT"), nullable(body, "outboundAllowlist"), integer(body, "connectTimeoutMs", 5000),
                    integer(body, "readTimeoutMs", 30000), longValue(body, "maxResponseBytes", 10485760L),
                    text(body.get("tlsMode"), "VERIFY_IDENTITY"), nullable(body, "clientCertRef"), nullable(body, "commonHeadersJson"),
                    authType, nullableValue(credentialRef), nullable(body, "authConfigJson"), integer(body, "status", 1), "system", now, "system", now);
        } else {
            int changed = jdbc.update("""
                    update data_pull_service_t set service_name=?,service_code=?,base_url=?,path_prefix=?,protocol=?,service_type=?,
                      media_type=?,network_mode=?,outbound_allowlist=?,connect_timeout_ms=?,read_timeout_ms=?,max_response_bytes=?,
                      tls_mode=?,client_cert_ref=?,common_headers_json=?,auth_type=?,credential_ref=?,auth_config_json=?,status=?,
                      updated_by=?,updated_time=? where tid=? and tenant_id=? and datasource_id=? and is_del=0
                    """, serviceName, serviceCode, baseUrl, nullable(body, "pathPrefix"), scheme.toUpperCase(Locale.ROOT),
                    text(body.get("serviceType"), "REST"), text(body.get("mediaType"), "application/json"), text(body.get("networkMode"), "DIRECT"),
                    nullable(body, "outboundAllowlist"), integer(body, "connectTimeoutMs", 5000), integer(body, "readTimeoutMs", 30000),
                    longValue(body, "maxResponseBytes", 10485760L), text(body.get("tlsMode"), "VERIFY_IDENTITY"), nullable(body, "clientCertRef"),
                    nullable(body, "commonHeadersJson"), authType, nullableValue(credentialRef), nullable(body, "authConfigJson"), integer(body, "status", 1),
                    "system", now, id, tenantId, datasourceId);
            if (changed != 1) throw new IllegalArgumentException("接口服务不存在或不属于当前租户");
        }
        return Map.of("tid", id, "credentialRef", credentialRef, "secretStored", false);
    }

    @Comment("保存当前租户逻辑表的 API 拉取规则；规则保存本身不会创建或启动 NiFi 任务")
    public Map<String, Object> saveRule(Map<String, Object> body) {
        String tenantId = tenantId();
        String datasourceId = required(body, "datasourceId", "请选择数据源");
        String tableId = required(body, "sourceTableId", "请选择逻辑数据表");
        String serviceId = required(body, "serviceId", "请选择接口服务");
        assertApiPullDatasource(tenantId, datasourceId);
        assertTable(tenantId, datasourceId, tableId);
        assertService(tenantId, datasourceId, serviceId);
        String triggerMode = text(body.get("triggerMode"), "MANUAL").toUpperCase(Locale.ROOT);
        if (!TRIGGER_TYPES.contains(triggerMode)) throw new IllegalArgumentException("不支持的触发方式");
        String cron = text(body.get("cronExpression"), "");
        if ("CRON".equals(triggerMode) && cron.isBlank()) throw new IllegalArgumentException("定时任务必须填写 Cron 表达式");
        String endpointPath = required(body, "endpointPath", "请填写接口路径");
        if (!endpointPath.startsWith("/")) throw new IllegalArgumentException("接口路径必须以 / 开头");
        String id = text(body.get("tid"), "");
        LocalDateTime now = LocalDateTime.now();
        JdbcTemplate jdbc = jdbc(tenantId);
        Object[] values = {tenantId,datasourceId,tableId,serviceId,required(body,"ruleName","请填写拉取规则名称"),
                text(body.get("endpointMethod"),"GET").toUpperCase(Locale.ROOT),endpointPath,nullable(body,"requestTemplateJson"),
                nullable(body,"responseConfigJson"),nullable(body,"paginationConfigJson"),nullable(body,"incrementalConfigJson"),
                nullable(body,"orchestrationConfigJson"),nullable(body,"mappingConfigJson"),nullable(body,"qualityConfigJson"),
                nullable(body,"deleteConfigJson"),nullable(body,"runtimeConfigJson"),triggerMode,nullableValue(cron),nullable(body,"nifiTaskId"),
                integer(body,"enabled",0),"system",now,"system",now};
        if (id.isBlank()) {
            id=id();
            jdbc.update("""
                    insert into data_pull_rule_t(tid,tenant_id,datasource_id,source_table_id,service_id,rule_name,endpoint_method,
                     endpoint_path,request_template_json,response_config_json,pagination_config_json,incremental_config_json,
                     orchestration_config_json,mapping_config_json,quality_config_json,delete_config_json,runtime_config_json,
                     trigger_mode,cron_expression,nifi_task_id,enabled,created_by,created_time,updated_by,updated_time,is_del)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                    """, concat(id, values));
        } else {
            int changed = jdbc.update("""
                    update data_pull_rule_t set datasource_id=?,source_table_id=?,service_id=?,rule_name=?,endpoint_method=?,endpoint_path=?,
                     request_template_json=?,response_config_json=?,pagination_config_json=?,incremental_config_json=?,
                     orchestration_config_json=?,mapping_config_json=?,quality_config_json=?,delete_config_json=?,runtime_config_json=?,
                     trigger_mode=?,cron_expression=?,nifi_task_id=?,enabled=?,updated_by=?,updated_time=?
                     where tid=? and tenant_id=? and is_del=0
                    """, datasourceId, tableId, serviceId, required(body,"ruleName","请填写拉取规则名称"),
                    text(body.get("endpointMethod"),"GET").toUpperCase(Locale.ROOT), endpointPath,
                    nullable(body,"requestTemplateJson"), nullable(body,"responseConfigJson"), nullable(body,"paginationConfigJson"),
                    nullable(body,"incrementalConfigJson"), nullable(body,"orchestrationConfigJson"), nullable(body,"mappingConfigJson"),
                    nullable(body,"qualityConfigJson"), nullable(body,"deleteConfigJson"), nullable(body,"runtimeConfigJson"),
                    triggerMode, nullableValue(cron), nullable(body,"nifiTaskId"), integer(body,"enabled",0), "system", now, id, tenantId);
            if (changed != 1) throw new IllegalArgumentException("拉取规则不存在或不属于当前租户");
        }
        return Map.of("tid",id,"createdNifiTask",false,"message","规则已保存；请在数据汇聚页显式创建 NiFi 任务后再启用调度");
    }

    /** Returns the server-side API source settings used when the data-access task builds its NiFi DSL. */
    @Comment("按数据源与逻辑表解析 NiFi API 来源节点配置；返回凭据引用，不返回凭据明文")
    public Map<String, Object> resolvePipelineSource(String datasourceId, String tableName) {
        String tenantId = tenantId();
        requireId(datasourceId, "数据源标识不能为空");
        requireId(tableName, "逻辑表名称不能为空");
        Map<String, Object> source = requireApiPullDatasource(tenantId, datasourceId);
        Map<String, Object> item = apiPullItems(text(source.get("pool_cfg"), "")).stream()
                .filter(candidate -> tableName.equalsIgnoreCase(text(candidate.get("tableName"), "")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("第一步未找到与逻辑表匹配的 API 定义：" + tableName));
        String baseUrl = required(item, "baseUrl", "API 定义缺少 Base URL").replaceAll("/+$", "");
        String endpointPath = required(item, "endpointPath", "API 定义缺少接口路径");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", endpointPath.startsWith("http://") || endpointPath.startsWith("https://")
                ? endpointPath : baseUrl + (endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath));
        config.put("method", text(item.get("endpointMethod"), "GET").toUpperCase(Locale.ROOT));
        config.put("apiProtocol", text(item.get("apiProtocol"), "REST"));
        config.put("captureScope", text(item.get("captureScope"), "full"));
        config.put("paginationMode", text(item.get("paginationMode"), "none"));
        config.put("contentType", text(item.get("apiProtocol"), "REST").equalsIgnoreCase("SOAP")
                ? "text/xml" : "application/json");
        config.put("requestBody", text(item.get("requestTemplateJson"), ""));
        String headers = text(item.get("commonHeadersJson"), "");
        rejectSensitiveHeaders(headers);
        config.put("commonHeadersJson", headers);
        config.put("responseExtractPath", text(item.get("responseExtractPath"), ""));
        config.put("responseConfigJson", text(item.get("responseConfigJson"), ""));
        config.put("paginationConfigJson", text(item.get("paginationConfigJson"), ""));
        config.put("incrementalConfigJson", text(item.get("incrementalConfigJson"), ""));
        config.put("runtimeConfigJson", text(item.get("runtimeConfigJson"), ""));
        config.put("authType", text(item.get("authType"), "NONE").toUpperCase(Locale.ROOT));
        config.put("credentialRef", text(item.get("credentialRef"), ""));
        applySchedule(config, item);
        return config;
    }

    public static void applySchedule(Map<String, Object> config, Map<String, Object> item) {
        Object saved = item.get("captureSchedule");
        Map<?, ?> schedule = saved instanceof Map<?, ?> map ? map : Map.of();
        String mode = text(schedule.get("mode"), text(item.get("scheduleMode"), "")).toLowerCase(Locale.ROOT);
        String cron = text(schedule.get("cronExpression"), text(item.get("cronExpression"), ""));
        if (!cron.isBlank()) {
            config.put("batchSchedulePolicy", "CUSTOM");
            config.put("scheduleMode", "cron");
            config.put("schedulingStrategy", "CRON_DRIVEN");
            config.put("schedulingPeriod", cron);
        } else if ("interval".equals(mode)) {
            String minutes = text(schedule.get("intervalMinutes"), text(item.get("scheduleIntervalMinutes"), ""));
            int interval = Integer.parseInt(minutes);
            if (interval < 1 || interval > 10080) throw new IllegalArgumentException("API 抓取间隔必须为 1 至 10080 分钟");
            config.put("batchSchedulePolicy", "CUSTOM");
            config.put("scheduleMode", "interval");
            config.put("schedulingStrategy", "TIMER_DRIVEN");
            config.put("schedulingPeriod", interval + " min");
        } else if ("once".equals(mode)) {
            config.put("batchSchedulePolicy", "CUSTOM");
            config.put("scheduleMode", "once");
            config.put("schedulingStrategy", "TIMER_DRIVEN");
            config.put("schedulingPeriod", "0 sec");
        } else if ("time".equals(mode) || "CRON".equalsIgnoreCase(text(item.get("triggerMode"), ""))) {
            throw new IllegalArgumentException("定时 API 缺少 Cron 表达式，请重新保存抓取配置");
        } else {
            config.putAll(com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy.markDefault(config));
        }
    }

    /**
     * NiFi controller callback after a pipeline is created. The editable HTTP
     * bind interface has its own workflow; this hook keeps pipeline creation
     * independent from a second HTTP request and applies the same tenant guard.
     */
    public Map<String, Object> bindTask(String datasourceId, String sourceTableId, String pipelineId) {
        String tenantId = tenantId();
        requireId(datasourceId, "数据源标识不能为空");
        requireId(sourceTableId, "来源表标识不能为空");
        requireId(pipelineId, "NiFi 流程标识不能为空");
        JdbcTemplate jdbc = jdbc(tenantId);
        List<Map<String, Object>> datasource = jdbc.queryForList(
                "select db_type,pool_cfg from db_datasource_t where tid=? and tenant_id=? and is_del=0",
                datasourceId, tenantId);
        if (datasource.isEmpty()) {
            throw new IllegalArgumentException("数据源不存在或不属于当前租户");
        }
        String type = text(datasource.get(0).get("db_type"), "").toLowerCase(Locale.ROOT);
        String pool = text(datasource.get(0).get("pool_cfg"), "")
                .replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        if (!"api".equals(type) || !(pool.contains("\"accessmode\":\"capture\"")
                || pool.contains("\"accessmode\":\"pull\""))) {
            return Map.of("applicable", false, "message", "当前来源不是 API 数据拉取，未绑定表规则");
        }
        int changed = jdbc.update("""
                update data_pull_rule_t
                   set nifi_task_id=?,updated_by=?,updated_time=?
                 where tenant_id=? and datasource_id=? and source_table_id=? and is_del=0
                """, pipelineId, "system", LocalDateTime.now(), tenantId, datasourceId, sourceTableId);
        return Map.of("applicable", true, "pipelineId", pipelineId, "boundRules", changed,
                "message", changed > 0
                        ? "表级拉取规则已绑定 NiFi 任务；可在画布中部署、手动启动或按 Cron 调度"
                        : "当前表尚未同步出 API 拉取规则，请返回第二步保存表标注后重试");
    }

    private String tenantId(){ return TenantContext.requireTenantId(); }
    private JdbcTemplate jdbc(String tenantId){ DataSource ds=tenants.dataSourceForTenant(tenantId); return new JdbcTemplate(ds); }
    private List<Map<String, Object>> apiPullItems(String poolCfg) {
        if (poolCfg == null || poolCfg.isBlank()) return List.of();
        try {
            Map<String, Object> root = JSON.readValue(poolCfg, new TypeReference<>() { });
            Object value = root.get("apiPullItems");
            if (!(value instanceof List<?> list)) return List.of();
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    map.forEach((key, value1) -> normalized.put(String.valueOf(key), value1));
                    result.add(normalized);
                }
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalArgumentException("数据源中的 API 拉取配置不是有效 JSON", exception);
        }
    }
    private static String serviceCode(String tableName, String serviceName) {
        String normalized = (tableName + "_" + serviceName).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isBlank() || !Character.isLetter(normalized.charAt(0))) normalized = "api_" + normalized;
        return normalized.length() > 96 ? normalized.substring(0, 96) : normalized;
    }
    /**
     * The table step is shared by relational databases, FTP, Kafka, push templates, and API pull.
     * Keep this check server-side as a second boundary: a forged request must not attach API rules to
     * any of the existing access modes even if the low-code page is bypassed.
     */
    private void assertApiPullDatasource(String tenant, String id) {
        requireApiPullDatasource(tenant, id);
    }

    private Map<String, Object> requireApiPullDatasource(String tenant,String id){
        List<Map<String, Object>> rows = jdbc(tenant).queryForList(
                "select db_type, pool_cfg from db_datasource_t where tid=? and tenant_id=? and is_del=0", id, tenant);
        if (rows.isEmpty()) throw new IllegalArgumentException("数据源不存在或不属于当前租户");
        Map<String, Object> row = rows.get(0);
        String dbType = text(row.get("db_type"), "").toLowerCase(Locale.ROOT);
        String poolCfg = text(row.get("pool_cfg"), "").replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        boolean apiCapture = "api".equals(dbType)
                && (poolCfg.contains("\"accessmode\":\"capture\"") || poolCfg.contains("\"accessmode\":\"pull\""));
        if (!apiCapture) {
            throw new IllegalArgumentException("API 拉取规则仅适用于接入方式为“数据拉取”的 API 数据源");
        }
        return rows.getFirst();
    }
    private void assertTable(String tenant,String source,String table){ if(jdbc(tenant).queryForObject("select count(1) from db_table_t where tid=? and datasource_id=? and tenant_id=? and is_del=0",Integer.class,table,source,tenant)==0) throw new IllegalArgumentException("逻辑数据表不存在或不属于当前数据源"); }
    private void assertService(String tenant,String source,String service){ if(jdbc(tenant).queryForObject("select count(1) from data_pull_service_t where tid=? and datasource_id=? and tenant_id=? and is_del=0",Integer.class,service,source,tenant)==0) throw new IllegalArgumentException("接口服务不存在或不属于当前数据源"); }
    private static void requireId(String value,String message){ if(value==null||value.isBlank()) throw new IllegalArgumentException(message); }
    private static String required(Map<String,Object> b,String k,String message){ String v=text(b.get(k),""); if(v.isBlank())throw new IllegalArgumentException(message);return v; }
    private static String text(Object value,String fallback){ return value==null?fallback:String.valueOf(value).trim(); }
    private static String nullable(Map<String,Object> b,String k){ return nullableValue(text(b.get(k),"")); }
    private static String nullableValue(String value){ return value==null||value.isBlank()?null:value; }
    private static int integer(Map<String,Object> b,String k,int fallback){ try{return b.get(k)==null?fallback:Integer.parseInt(String.valueOf(b.get(k)));}catch(Exception e){return fallback;} }
    private static long longValue(Map<String,Object> b,String k,long fallback){try{return b.get(k)==null?fallback:Long.parseLong(String.valueOf(b.get(k)));}catch(Exception e){return fallback;} }
    private static String id(){ return UUID.randomUUID().toString().replace("-",""); }
    private static Object[] concat(Object first,Object[] values){ Object[] result=new Object[values.length+1];result[0]=first;System.arraycopy(values,0,result,1,values.length);return result; }
    private static void rejectSensitiveHeaders(String json){ String lower=json.toLowerCase(Locale.ROOT); if(lower.contains("authorization")||lower.contains("token")||lower.contains("api-key")||lower.contains("password")||lower.contains("secret")) throw new IllegalArgumentException("通用请求头不能保存认证或密钥字段，请使用凭据引用"); }
}
