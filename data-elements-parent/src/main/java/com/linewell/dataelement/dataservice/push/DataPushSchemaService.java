package com.linewell.dataelement.dataservice.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes and validates push contracts derived from the registration master
 * tables. Snapshot rows are an audit/version boundary, never a replacement for
 * db_datasource_t/db_table_t/db_table_column_t.
 */
@Service
public class DataPushSchemaService {
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() { };
    private final TenantDataSourceRegistry tenants;
    private final ObjectMapper json;
    private final DataPushFjghccDeliveryService fjghcc;

    public DataPushSchemaService(TenantDataSourceRegistry tenants, ObjectMapper json, DataPushFjghccDeliveryService fjghcc) {
        this.tenants = tenants;
        this.json = json;
        this.fjghcc = fjghcc;
    }

    @Transactional
    public List<Map<String, Object>> publish(String tenantId, String datasourceId) {
        JdbcTemplate jdbc = jdbc(tenantId);
        Source source = source(jdbc, tenantId, datasourceId);
        requirePushSource(source);
        List<Map<String, Object>> tables = jdbc.queryForList("""
                select tid, table_name from db_table_t
                 where tenant_id = ? and datasource_id = ? and asset_status = 2 and (is_del = 0 or is_del is null)
                 order by table_name, tid
                """, tenantId, datasourceId);
        if (tables.isEmpty()) throw new IllegalStateException("数据源尚无登记成功的数据表，无法发布推送契约");
        Map<String,List<Map<String,Object>>> fieldsByTable = columnsForTables(jdbc,tenantId,tables.stream().map(table->text(table.get("tid"))).toList());
        Map<String,Map<String,Object>> schemasByTable = new HashMap<>();
        for(Map<String,Object> schema:jdbc.queryForList("""
                select tid, table_id, schema_version, schema_hash from data_push_schema_t
                 where tenant_id=? and datasource_id=? and is_del=0
                """, tenantId, datasourceId)) schemasByTable.putIfAbsent(text(schema.get("table_id")),schema);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> table : tables) {
            String tableId=text(table.get("tid"));
            result.add(publishTable(jdbc, tenantId, source, tableId, text(table.get("table_name")),
                    fieldsByTable.getOrDefault(tableId,List.of()), schemasByTable.get(tableId)));
        }
        return result;
    }

    /** Validates that a tenant-owned source is eligible to receive a data-source API key. */
    public void requirePushDatasource(String tenantId, String datasourceId) {
        requirePushAccessMode(source(jdbc(tenantId), tenantId, datasourceId));
    }

    /**
     * Builds one complete, non-secret standalone relay snapshot.  Publishing
     * first makes the same registration state authoritative for both the UI
     * contract and the relay's local validation tables.
     */
    @Transactional
    public Map<String, Object> standaloneSnapshot(String tenantId, String datasourceId, String clientId) {
        JdbcTemplate jdbc = jdbc(tenantId);
        Source source = source(jdbc, tenantId, datasourceId);
        requirePushSource(source);
        List<Map<String, Object>> published = publish(tenantId, datasourceId);
        Map<String, Map<String, Object>> schemaByTable = new LinkedHashMap<>();
        for (Map<String, Object> schema : published) schemaByTable.put(text(schema.get("tableId")), schema);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select t.tid, t.table_name, t.table_name_cn, t.table_comment
                  from db_table_t t
                 where t.tenant_id=? and t.datasource_id=? and t.asset_status=2
                   and (t.is_del=0 or t.is_del is null)
                 order by t.table_name, t.tid
                """, tenantId, datasourceId);
        Map<String,List<Map<String,Object>>> columnsByTable=columnsForTables(jdbc,tenantId,rows.stream().map(row->text(row.get("tid"))).toList());
        List<Map<String, Object>> tables = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String tableId = text(row.get("tid"));
            Map<String, Object> schema = schemaByTable.get(tableId);
            if (schema == null) continue;
            List<Map<String, Object>> columns = columnsByTable.getOrDefault(tableId,List.of());
            List<Map<String, Object>> normalizedColumns = new ArrayList<>();
            for (Map<String, Object> column : columns) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("columnName", text(column.get("column_name")));
                item.put("columnComment", text(column.get("column_comment")));
                item.put("dataType", firstText(column, "column_type", "data_type"));
                item.put("ordinal", integer(column.get("ordinal_position"), 0));
                item.put("required", required(column));
                item.put("enabled", true);
                normalizedColumns.add(item);
            }
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("tableId", tableId);
            table.put("tableName", text(row.get("table_name")));
            table.put("tableComment", firstText(row, "table_name_cn", "table_comment", "table_name"));
            table.put("schemaVersion", String.valueOf(longValue(schema.get("schemaVersion"), 1L)));
            table.put("enabled", true);
            table.put("columns", normalizedColumns);
            tables.add(table);
        }
        if (tables.isEmpty()) throw new IllegalStateException("数据源尚无可同步的已登记推送表");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("datasourceId", source.id());
        snapshot.put("datasourceName", source.name());
        snapshot.put("sourceSystemId", "");
        snapshot.put("sourceSystemName", "");
        snapshot.put("contractVersion", String.valueOf(System.currentTimeMillis()));
        snapshot.put("enabled", true);
        snapshot.put("clientIds", List.of(clientId));
        snapshot.put("tables", tables);
        return snapshot;
    }

    public Map<String, Object> validate(String tenantId, String clientId, String channel, Map<String, Object> batch) {
        String datasourceId = code(batch.get("datasourceId"), "datasourceId", 64);
        String tableName = code(batch.get("tableName"), "tableName", 255);
        List<Map<String, Object>> records = records(batch.get("records"));
        JdbcTemplate jdbc = jdbc(tenantId);
        Source source = source(jdbc, tenantId, datasourceId);
        requirePushSource(source);
        String configuredClient = text(pushDelivery(source.poolCfg()).get("relayClientId"));
        if (!configuredClient.isBlank() && !configuredClient.equals(clientId)) throw new IllegalArgumentException("调用方不属于该推送数据源");
        // The tenant database can contain historical tables created with a
        // different MySQL collation.  Matching table_name in SQL then fails
        // before any contract rule is evaluated (1267 illegal mix of
        // collations).  The source/table scope is already narrow; compare the
        // ordinary identifier in Java instead of relying on database collation.
        List<Map<String, Object>> candidates = jdbc.queryForList("""
                select s.tid, s.table_id, s.table_name, s.schema_version, s.schema_hash, s.delivery_config
                  from data_push_schema_t s
                 where s.tenant_id = ? and s.datasource_id = ? and s.status = 'PUBLISHED'
                   and s.is_del = 0
                """, tenantId, datasourceId).stream()
                .filter(item -> tableName.equalsIgnoreCase(text(item.get("table_name"))))
                .toList();
        Set<String> activeTableIds=new HashSet<>();
        List<String> candidateIds=candidates.stream().map(item->text(item.get("table_id"))).distinct().toList();
        for(int offset=0;offset<candidateIds.size();offset+=500){List<String> chunk=candidateIds.subList(offset,Math.min(offset+500,candidateIds.size()));List<Object> args=new ArrayList<>();args.add(tenantId);args.add(datasourceId);args.addAll(chunk);
            String marks=String.join(",",Collections.nCopies(chunk.size(),"?"));
            for(var table:jdbc.queryForList("select tid from db_table_t where tenant_id=? and datasource_id=? and asset_status=2 and (is_del=0 or is_del is null) and tid in ("+marks+")",args.toArray()))activeTableIds.add(text(table.get("tid")));
        }
        List<Map<String,Object>> schemas=candidates.stream().filter(item->activeTableIds.contains(text(item.get("table_id")))).toList();
        if (schemas.isEmpty()) throw new IllegalArgumentException("数据表未完成推送契约发布，请重新完成数据源登记");
        Map<String, Object> schema = schemas.getFirst();
        long currentVersion = ((Number) schema.get("schema_version")).longValue();
        if (batch.get("schemaVersion") != null && longValue(batch.get("schemaVersion"), currentVersion) != currentVersion)
            throw new IllegalArgumentException("推送契约版本已更新，请按最新字段定义推送");
        List<Map<String, Object>> fields = jdbc.queryForList("""
                select field_name, data_type, required_flag from data_push_schema_field_t
                 where tenant_id = ? and schema_id = ? and is_del = 0
                 order by ordinal_position, tid
                """, tenantId, text(schema.get("tid")));
        validateRecords(records, fields);
        Map<String, Object> delivery = map(schema.get("delivery_config"));
        String channelKey = switch (channel) {
            case "table-to-ftp" -> "ftp";
            case "table-to-kafka" -> "kafka";
            default -> throw new IllegalArgumentException("不支持的推送通道");
        };
        Map<String, Object> target = map(delivery.get(channelKey));
        String destination;
        String targetType;
        if (channelKey.equals("ftp")) {
            // Resolve ODS/FJGHCC for the active tenant at delivery time.  Do not
            // persist an environment-specific FTP hostname or root in a source contract.
            fjghcc.requireTarget(tenantId);
            destination = "ODS/FJGHCC";
            targetType = "PLATFORM_FTP";
        } else {
            destination = firstText(target, "topic");
            if (destination.isBlank()) throw new IllegalStateException("登记数据源尚未配置 Kafka Topic");
            targetType = "KAFKA";
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("targetType", targetType);
        answer.put("destination", destination);
        answer.put("datasourceId", datasourceId);
        answer.put("tableId", text(schema.get("table_id")));
        answer.put("tableName", tableName);
        answer.put("schemaVersion", currentVersion);
        answer.put("schemaHash", text(schema.get("schema_hash")));
        answer.put("recordCount", records.size());
        return answer;
    }

    public Map<String, Object> deliverFtp(String tenantId, String clientId, Map<String, Object> batch, String deliveryId) {
        Map<String, Object> contract = validate(tenantId, clientId, "table-to-ftp", batch);
        return fjghcc.deliver(tenantId, text(contract.get("datasourceId")), text(contract.get("tableName")),
                code(deliveryId, "deliveryId", 128), records(batch.get("records")), longValue(contract.get("schemaVersion"), 1L));
    }

    /**
     * Safe, caller-facing projection of a published push contract. It exposes
     * table and field requirements, never a delivery endpoint, FTP credential,
     * Kafka topic, relay key, or any value from a connection configuration.
     */
    public Map<String, Object> contract(String tenantId, String datasourceId) {
        JdbcTemplate jdbc = jdbc(tenantId);
        Source source = source(jdbc, tenantId, datasourceId);
        requirePushSource(source);
        List<Map<String, Object>> schemas = jdbc.queryForList("""
                select s.tid, s.table_id, s.table_name, s.schema_version, s.schema_hash, s.published_time
                  from data_push_schema_t s
                  join db_table_t t on convert(t.tid using utf8mb4) collate utf8mb4_general_ci
                                     = convert(s.table_id using utf8mb4) collate utf8mb4_general_ci
                     and convert(t.tenant_id using utf8mb4) collate utf8mb4_general_ci
                         = convert(s.tenant_id using utf8mb4) collate utf8mb4_general_ci
                 where s.tenant_id=? and s.datasource_id=? and s.status='PUBLISHED' and s.is_del=0
                   and t.asset_status=2 and (t.is_del=0 or t.is_del is null)
                 order by s.table_name, s.tid
                """, tenantId, datasourceId);
        List<Map<String, Object>> tables = new ArrayList<>();
        Map<String,List<Map<String,Object>>> fieldsBySchema=new HashMap<>();
        List<String> schemaIds=schemas.stream().map(schema->text(schema.get("tid"))).distinct().toList();
        for(int offset=0;offset<schemaIds.size();offset+=500){List<String> chunk=schemaIds.subList(offset,Math.min(offset+500,schemaIds.size()));List<Object> args=new ArrayList<>();args.add(tenantId);args.addAll(chunk);
            String marks=String.join(",",Collections.nCopies(chunk.size(),"?"));
            for(var field:jdbc.queryForList("select schema_id,field_name,data_type,required_flag,ordinal_position from data_push_schema_field_t where tenant_id=? and schema_id in ("+marks+") and is_del=0 order by schema_id,ordinal_position,tid",args.toArray()))
                fieldsBySchema.computeIfAbsent(text(field.get("schema_id")),ignored->new ArrayList<>()).add(field);
        }
        for (Map<String, Object> schema : schemas) {
            List<Map<String, Object>> fields = fieldsBySchema.getOrDefault(text(schema.get("tid")),List.of());
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Map<String, Object> field : fields) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fieldName", text(field.get("field_name")));
                item.put("dataType", text(field.get("data_type")));
                item.put("required", integer(field.get("required_flag"), 0) == 1);
                item.put("ordinal", integer(field.get("ordinal_position"), 0));
                normalized.add(item);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("schemaId", text(schema.get("tid")));
            item.put("tableId", text(schema.get("table_id")));
            item.put("tableName", text(schema.get("table_name")));
            item.put("schemaVersion", longValue(schema.get("schema_version"), 1L));
            item.put("schemaHash", text(schema.get("schema_hash")));
            item.put("publishedTime", schema.get("published_time"));
            item.put("fieldCount", normalized.size());
            item.put("fields", normalized);
            tables.add(item);
        }
        // One stable public entry point.  deliveryType chooses the registered
        // delivery channel; callers never receive or control an FTP directory
        // or Kafka topic.  The relay still translates this to its internal
        // route code so historical credentials and delivery audit remain valid.
        Map<String, Object> dataPush = new LinkedHashMap<>();
        dataPush.put("method", "POST"); dataPush.put("path", "/api/v1/data-push");
        dataPush.put("description", "统一表批次推送入口；以 deliveryType 选择已登记并获授权的 FTP 或 Kafka 投递通道");
        dataPush.put("deliveryTypes", List.of(
                Map.of("value", "FTP", "description", "写入当前环境 ODS/FJGHCC 关联的文件存储"),
                Map.of("value", "KAFKA", "description", "写入本数据源已配置且获授权的 Kafka Topic")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasourceId", source.id());
        result.put("datasourceName", source.name());
        result.put("accessMode", "receive");
        result.put("requestHeaders", List.of(
                Map.of("name", "Content-Type", "value", "application/json", "required", true, "description", "请求体必须为 JSON"),
                Map.of("name", "X-Api-Key", "value", "业务调用方身份密钥", "required", true, "description", "用于识别推送业务数据的调用方，并校验其对当前数据源及已登记数据表的推送权限；平台不保存密钥明文，请勿放入请求体或前端代码中"),
                Map.of("name", "Idempotency-Key", "value", "每次逻辑请求唯一 UUID", "required", true, "description", "相同键重试返回同一受理结果")));
        result.put("requiredParameters", List.of(
                Map.of("name", "deliveryType", "required", true, "description", "投递类型，仅支持 FTP 或 KAFKA；目标地址由登记契约确定"),
                Map.of("name", "datasourceId", "required", true, "description", "固定为当前数据源编码"),
                Map.of("name", "tableName", "required", true, "description", "必须为下方已发布的数据表英文名"),
                Map.of("name", "batchId", "required", false, "description", "调用方批次标识，建议可追溯且不可复用"),
                Map.of("name", "occurredAt", "required", false, "description", "批次产生时间，ISO 8601 格式"),
                Map.of("name", "schemaVersion", "required", false, "description", "不传默认使用当前版本；传入时必须与表契约一致"),
                Map.of("name", "records", "required", true, "description", "1 至 10,000 条记录，每条仅可包含已登记字段")));
        result.put("endpoints", Map.of("dataPush", dataPush));
        result.put("tables", tables);
        return result;
    }

    private Map<String, Object> publishTable(JdbcTemplate jdbc, String tenantId, Source source, String tableId, String tableName,
            List<Map<String,Object>> fields,Map<String,Object> old) {
        if (fields.isEmpty()) throw new IllegalStateException("数据表 " + tableName + " 未登记字段，无法发布推送契约");
        List<Map<String, Object>> canonical = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            String name = code(field.get("column_name"), "field", 255);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name); item.put("type", firstText(field, "column_type", "data_type"));
            item.put("required", required(field)); item.put("ordinal", integer(field.get("ordinal_position"), 0));
            canonical.add(item);
        }
        String hash = sha256(write(canonical));
        Map<String, Object> delivery = pushDelivery(source.poolCfg());
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String schemaId;
        long version;
        if (old==null) {
            schemaId = NumericId.nextId(); version = 1L;
            jdbc.update("""
                    insert into data_push_schema_t(tid,datasource_id,table_id,table_name,schema_version,schema_hash,status,delivery_config,tenant_id,published_time,updated_time,is_del)
                    values (?,?,?,?,? ,?,'PUBLISHED',?,?,?, ?,0)
                    """, schemaId, source.id(), tableId, tableName, version, hash, write(delivery), tenantId, now, now);
        } else {
            Map<String, Object> current = old; schemaId = text(current.get("tid"));
            version = text(current.get("schema_hash")).equals(hash) ? ((Number) current.get("schema_version")).longValue() : ((Number) current.get("schema_version")).longValue() + 1L;
            jdbc.update("""
                    update data_push_schema_t set table_name=?, schema_version=?, schema_hash=?, status='PUBLISHED', delivery_config=?, published_time=?, updated_time=?
                     where tid=? and tenant_id=?
                    """, tableName, version, hash, write(delivery), now, now, schemaId, tenantId);
            jdbc.update("update data_push_schema_field_t set is_del=1 where schema_id=? and tenant_id=? and is_del=0", schemaId, tenantId);
        }
        for (Map<String, Object> field : canonical) {
            int changed = jdbc.update("""
                    update data_push_schema_field_t
                       set data_type=?, required_flag=?, ordinal_position=?, is_del=0
                     where schema_id=? and field_name=? and tenant_id=?
                    """, field.get("type"), Boolean.TRUE.equals(field.get("required")) ? 1 : 0, field.get("ordinal"), schemaId, field.get("name"), tenantId);
            if (changed == 0) {
                jdbc.update("""
                        insert into data_push_schema_field_t(tid,schema_id,field_name,data_type,required_flag,ordinal_position,tenant_id,is_del)
                        values (?,?,?,?,?,?,?,0)
                        """, NumericId.nextId(), schemaId, field.get("name"), field.get("type"), Boolean.TRUE.equals(field.get("required")) ? 1 : 0, field.get("ordinal"), tenantId);
            }
        }
        return Map.of("schemaId", schemaId, "tableId", tableId, "tableName", tableName, "schemaVersion", version, "schemaHash", hash);
    }

    private Source source(JdbcTemplate jdbc, String tenantId, String datasourceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tid, db_name, asset_status, pool_cfg from db_datasource_t
                 where tid=? and tenant_id=? and (is_del=0 or is_del is null)
                """, datasourceId, tenantId);
        if (rows.isEmpty()) throw new IllegalArgumentException("登记数据源不存在或无权访问");
        Map<String, Object> row = rows.getFirst();
        return new Source(text(row.get("tid")), text(row.get("db_name")), integer(row.get("asset_status"), 0), map(row.get("pool_cfg")));
    }
    private Map<String,List<Map<String,Object>>> columnsForTables(JdbcTemplate jdbc,String tenantId,List<String> tableIds){
        Map<String,List<Map<String,Object>>> grouped=new LinkedHashMap<>();
        for(int offset=0;offset<tableIds.size();offset+=500){List<String> chunk=tableIds.subList(offset,Math.min(offset+500,tableIds.size()));List<Object> args=new ArrayList<>();args.add(tenantId);args.addAll(chunk);
            String marks=String.join(",",Collections.nCopies(chunk.size(),"?"));
            for(var column:jdbc.queryForList("select table_id,column_name,column_comment,data_type,column_type,nullable,primary_key,ordinal_position from db_table_column_t where tenant_id=? and table_id in ("+marks+") and (is_del=0 or is_del is null) order by table_id,ordinal_position,tid",args.toArray()))
                grouped.computeIfAbsent(text(column.get("table_id")),ignored->new ArrayList<>()).add(column);
        }
        return grouped;
    }
    private void requirePushSource(Source source) {
        if (source.assetStatus() != 1 && source.assetStatus() != 2)
            throw new IllegalStateException("数据源尚未完成登记");
        requirePushAccessMode(source);
    }
    private void requirePushAccessMode(Source source) {
        String mode = firstText(source.poolCfg(), "accessMode", "access_mode", "dataAccessMode", "data_access_mode").toLowerCase(Locale.ROOT);
        if (!Set.of("receive", "push").contains(mode)) throw new IllegalArgumentException("该数据源不是数据推送方式");
        /*
         * `accessMode=receive` is the registration flow's authoritative switch for a
         * data-push datasource.  Early registrations did not persist an additional
         * pushDelivery.enabled flag, even though the first-step UI has no such field.
         * Treat a missing flag as enabled for an already selected push source, while
         * retaining an explicit false as an administrator-controlled off switch.
         *
         * Delivery destinations are still checked independently in validate(), so
         * this compatibility path can never silently route a batch to a default FTP
         * directory or Kafka topic.
         */
        Map<String, Object> delivery = pushDelivery(source.poolCfg());
        if (delivery.containsKey("enabled") && !bool(delivery.get("enabled"))) {
            throw new IllegalStateException("数据源推送功能尚未启用");
        }
    }
    private void validateRecords(List<Map<String, Object>> records, List<Map<String, Object>> fields) {
        Map<String, Map<String, Object>> known = new LinkedHashMap<>();
        for (Map<String, Object> field : fields) known.put(text(field.get("field_name")), field);
        for (Map<String, Object> record : records) {
            for (String name : record.keySet()) if (!known.containsKey(name)) throw new IllegalArgumentException("存在未登记字段：" + name);
            for (Map.Entry<String, Map<String, Object>> item : known.entrySet()) {
                Object value = record.get(item.getKey());
                if (integer(item.getValue().get("required_flag"), 0) == 1 && (value == null || value instanceof String s && s.isBlank()))
                    throw new IllegalArgumentException("缺少必填字段：" + item.getKey());
                if (value != null && !compatible(value, text(item.getValue().get("data_type"))))
                    throw new IllegalArgumentException("字段类型不匹配：" + item.getKey());
            }
        }
    }
    private boolean compatible(Object value, String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("bool")) return value instanceof Boolean || "0".equals(value) || "1".equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) || "false".equalsIgnoreCase(String.valueOf(value));
        if (normalized.matches(".*(int|decimal|numeric|double|float|number|real).*")) return value instanceof Number || String.valueOf(value).matches("[-+]?\\d+(\\.\\d+)?");
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 10000) throw new IllegalArgumentException("records 必须是 1 到 10000 条记录");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) throw new IllegalArgumentException("records 中每条记录必须是对象");
            result.add(new LinkedHashMap<>((Map<String, Object>) map));
        }
        return result;
    }
    private Map<String, Object> pushDelivery(Map<String, Object> pool) { return map(pool.get("pushDelivery")); }
    private Map<String, Object> map(Object value) {
        try {
            if (value instanceof Map<?, ?> map) { @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) map; return new LinkedHashMap<>(typed); }
            if (value == null || String.valueOf(value).isBlank()) return new LinkedHashMap<>();
            return json.readValue(String.valueOf(value), MAP);
        } catch (Exception exception) { throw new IllegalArgumentException("数据源推送配置不是有效 JSON"); }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("无法生成推送契约", e); } }
    private String sha256(String text) { try { byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); return java.util.HexFormat.of().formatHex(hash); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String code(Object value, String name, int max) { String text = text(value); if (!text.matches("[A-Za-z0-9_.-]{1," + max + "}")) throw new IllegalArgumentException(name + " 格式不正确"); return text; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String firstText(Map<String, Object> values, String... keys) { for (String key : keys) { String value = text(values.get(key)); if (!value.isBlank()) return value; } return ""; }
    private int integer(Object value, int fallback) { if (value == null || String.valueOf(value).isBlank()) return fallback; return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value)); }
    private long longValue(Object value, long fallback) { if (value == null || String.valueOf(value).isBlank()) return fallback; return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); }
    private boolean required(Map<String, Object> field) { return integer(field.get("nullable"), 1) == 0 || integer(field.get("primary_key"), 0) == 1; }
    private boolean bool(Object value) { return value instanceof Boolean b ? b : Set.of("1", "true", "yes", "on").contains(text(value).toLowerCase(Locale.ROOT)); }
    private JdbcTemplate jdbc(String tenantId) { return new JdbcTemplate(tenants.dataSourceForTenant(tenantId)); }
    private record Source(String id, String name, int assetStatus, Map<String, Object> poolCfg) { }
}
