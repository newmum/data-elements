package com.haitong.registry;

import com.haitong.core.Json;
import com.haitong.core.RelayException;
import com.haitong.core.Rules;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local mirror of the source-platform datasource/table/column contract used
 * only by explicit standalone routes.  The platform sends complete datasource snapshots
 * individually or in a bounded atomic batch; tables, columns and caller grants absent
 * from that snapshot are logically deleted in the same transaction.
 */
@Repository
public class StandaloneRegistryStore {
    private final JdbcTemplate jdbc;
    private final Json json;

    public StandaloneRegistryStore(JdbcTemplate jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<Datasource> datasource(String datasourceId) {
        List<Datasource> rows = jdbc.query("""
                select datasource_id,datasource_name,is_enable,is_del
                  from ht_standalone_datasource where datasource_id=?
                """, (r, i) -> new Datasource(r.getString(1), r.getString(2), r.getBoolean(3), r.getBoolean(4)), datasourceId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<Table> table(String datasourceId, String tableName) {
        List<Table> rows = jdbc.query("""
                select datasource_id,table_name,schema_version,is_enable,is_del
                  from ht_standalone_table where datasource_id=? and table_name=?
                """, (r, i) -> new Table(r.getString(1), r.getString(2), r.getString(3), r.getBoolean(4), r.getBoolean(5)), datasourceId, tableName);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Set<String> clients(String datasourceId) {
        return Set.copyOf(jdbc.queryForList("""
                select client_id from ht_standalone_datasource_client
                 where datasource_id=? and is_enable=1 and is_del=0
                """, String.class, datasourceId));
    }

    public List<Column> columns(String datasourceId, String tableName) {
        return jdbc.query("""
                select column_name,is_required from ht_standalone_table_column
                 where datasource_id=? and table_name=? and is_enable=1 and is_del=0
                 order by ordinal_no,column_name
                """, (r, i) -> new Column(r.getString(1), r.getBoolean(2)), datasourceId, tableName);
    }

    @Transactional
    public SyncResult synchronize(String idempotencyKey, byte[] input) {
        String key = Rules.key(idempotencyKey);
        String payloadHash = Rules.sha256(input);
        List<SyncAudit> prior = jdbc.query("select payload_sha256,datasource_id from ht_standalone_registry_sync where idempotency_key=?",
                (r, i) -> new SyncAudit(r.getString(1), r.getString(2)), key);
        if (!prior.isEmpty()) {
            if (!Rules.constantEquals(prior.getFirst().payloadHash(), payloadHash)) throw RelayException.conflict();
            return new SyncResult(prior.getFirst().datasourceId(), true, 0, 0);
        }

        Definition definition = definition(input);
        long now = System.currentTimeMillis();
        int columns = apply(definition, now);
        try {
            jdbc.update("insert into ht_standalone_registry_sync(idempotency_key,payload_sha256,datasource_id,applied_at) values (?,?,?,?)",
                    key, payloadHash, definition.datasourceId(), now);
        } catch (DuplicateKeyException duplicate) {
            List<SyncAudit> concurrent = jdbc.query("select payload_sha256,datasource_id from ht_standalone_registry_sync where idempotency_key=?",
                    (r, i) -> new SyncAudit(r.getString(1), r.getString(2)), key);
            if (!concurrent.isEmpty() && Rules.constantEquals(concurrent.getFirst().payloadHash(), payloadHash))
                return new SyncResult(concurrent.getFirst().datasourceId(), true, definition.tables().size(), columns);
            throw RelayException.conflict();
        }
        return new SyncResult(definition.datasourceId(), false, definition.tables().size(), columns);
    }

    /** One bounded, atomic mirror update for all explicitly supplied sources. */
    @Transactional
    public BatchSyncResult synchronizeBatch(String idempotencyKey, byte[] input) {
        String key = Rules.key(idempotencyKey);
        String payloadHash = Rules.sha256(input);
        Map<String,Object> root = json.object(input);
        if (root.containsKey("datasource")) throw RelayException.invalid("datasource 与 datasources 不能同时提交");
        List<Object> raw = array(root.get("datasources"), "datasources 必须是数组");
        if (raw.isEmpty() || raw.size() > 100) throw RelayException.invalid("datasources 必须包含 1 到 100 个数据源");
        List<Definition> definitions = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int tables = 0, columns = 0;
        for (Object value : raw) {
            Definition definition = definition(object(value, "datasources 每项必须是对象"));
            if (!ids.add(definition.datasourceId())) throw RelayException.invalid("datasourceId 不得重复");
            definitions.add(definition);
            tables += definition.tables().size();
            columns += definition.tables().stream().mapToInt(t -> t.columns().size()).sum();
        }
        if (tables > 1000 || columns > 10000) throw RelayException.invalid("批量登记配置最多 1000 张表和 10000 个字段");
        String batchId = "batch-" + Rules.sha256(json.bytes(ids)).substring(0, 58);
        List<SyncAudit> prior = jdbc.query("select payload_sha256,datasource_id from ht_standalone_registry_sync where idempotency_key=?",
                (r, i) -> new SyncAudit(r.getString(1), r.getString(2)), key);
        if (!prior.isEmpty()) {
            if (!Rules.constantEquals(prior.getFirst().payloadHash(), payloadHash)) throw RelayException.conflict();
            return new BatchSyncResult(List.copyOf(ids), true, tables, columns);
        }
        long now = System.currentTimeMillis();
        // Reserve the audit key before changing mirrors. A concurrent retry
        // either observes this committed hash or rolls back as a conflict.
        try {
            jdbc.update("insert into ht_standalone_registry_sync(idempotency_key,payload_sha256,datasource_id,applied_at) values (?,?,?,?)",
                    key, payloadHash, batchId, now);
        } catch (DuplicateKeyException duplicate) { throw RelayException.conflict(); }
        applyBatch(definitions, now);
        return new BatchSyncResult(List.copyOf(ids), false, tables, columns);
    }

    /** Multi-row statements keep a 100-source mirror from making a round trip per field. */
    private void applyBatch(List<Definition> definitions,long now) {
        List<Object[]> sources=new ArrayList<>(),clients=new ArrayList<>(),tables=new ArrayList<>(),columns=new ArrayList<>();
        List<Object> args=new ArrayList<>();args.add(now);
        for(Definition source:definitions) {
            args.add(source.datasourceId());
            sources.add(new Object[]{source.datasourceId(),source.datasourceName(),source.sourceSystemId(),source.sourceSystemName(),source.contractVersion(),source.enabled(),0,now,now});
            for(String client:source.clientIds())clients.add(new Object[]{source.datasourceId(),client,1,0,now,now});
            for(TableDefinition table:source.tables()) {
                tables.add(new Object[]{source.datasourceId(),table.tableName(),table.tableId(),table.tableComment(),table.schemaVersion(),table.enabled(),0,now,now});
                for(ColumnDefinition column:table.columns())columns.add(new Object[]{source.datasourceId(),table.tableName(),column.columnName(),column.columnComment(),column.dataType(),column.ordinal(),column.required(),column.enabled(),0,now,now});
            }
        }
        String marks=String.join(",",java.util.Collections.nCopies(definitions.size(),"?"));
        for(String table:List.of("ht_standalone_datasource_client","ht_standalone_table_column","ht_standalone_table"))
            jdbc.update("update "+table+" set is_enable=0,is_del=1,updated_at=? where datasource_id in ("+marks+")",args.toArray());
        upsertBatch("ht_standalone_datasource","datasource_id,datasource_name,source_system_id,source_system_name,contract_version,is_enable,is_del,created_at,updated_at",Set.of("datasource_id"),sources);
        upsertBatch("ht_standalone_datasource_client","datasource_id,client_id,is_enable,is_del,created_at,updated_at",Set.of("datasource_id","client_id"),clients);
        upsertBatch("ht_standalone_table","datasource_id,table_name,table_id,table_comment,schema_version,is_enable,is_del,created_at,updated_at",Set.of("datasource_id","table_name"),tables);
        upsertBatch("ht_standalone_table_column","datasource_id,table_name,column_name,column_comment,data_type,ordinal_no,is_required,is_enable,is_del,created_at,updated_at",Set.of("datasource_id","table_name","column_name"),columns);
    }

    private void upsertBatch(String table,String columns,Set<String> keys,List<Object[]> rows) {
        String[] names=columns.split(",");
        String marks="("+String.join(",",java.util.Collections.nCopies(names.length,"?"))+")";
        String assignments=java.util.Arrays.stream(names).filter(n->!keys.contains(n)&&!n.equals("created_at"))
                .map(n->n+"=VALUES("+n+")").collect(java.util.stream.Collectors.joining(","));
        for(int offset=0;offset<rows.size();offset+=500) {
            List<Object[]> chunk=rows.subList(offset,Math.min(offset+500,rows.size()));
            List<Object> args=new ArrayList<>();for(Object[] row:chunk)java.util.Collections.addAll(args,row);
            jdbc.update("insert into "+table+" ("+columns+") values "+String.join(",",java.util.Collections.nCopies(chunk.size(),marks))+" ON DUPLICATE KEY UPDATE "+assignments,args.toArray());
        }
    }

    /** Writes only; all validation and the single idempotency read happen first. */
    private int apply(Definition definition, long now) {
        upsertDatasource(definition, now);
        jdbc.update("update ht_standalone_datasource_client set is_enable=0,is_del=1,updated_at=? where datasource_id=?", now, definition.datasourceId());
        jdbc.update("update ht_standalone_table_column set is_enable=0,is_del=1,updated_at=? where datasource_id=?", now, definition.datasourceId());
        jdbc.update("update ht_standalone_table set is_enable=0,is_del=1,updated_at=? where datasource_id=?", now, definition.datasourceId());
        for (String clientId : definition.clientIds()) upsertClient(definition.datasourceId(), clientId, now);
        int columns = 0;
        for (TableDefinition table : definition.tables()) {
            upsertTable(definition.datasourceId(), table, now);
            for (ColumnDefinition column : table.columns()) {
                upsertColumn(definition.datasourceId(), table.tableName(), column, now);
                columns++;
            }
        }
        return columns;
    }

    private void upsertDatasource(Definition source, long now) {
        int changed = jdbc.update("""
                update ht_standalone_datasource set datasource_name=?,source_system_id=?,source_system_name=?,contract_version=?,
                    is_enable=?,is_del=0,updated_at=? where datasource_id=?
                """, source.datasourceName(), source.sourceSystemId(), source.sourceSystemName(), source.contractVersion(), source.enabled(), now, source.datasourceId());
        if (changed == 0) jdbc.update("""
                insert into ht_standalone_datasource(datasource_id,datasource_name,source_system_id,source_system_name,contract_version,is_enable,is_del,created_at,updated_at)
                values (?,?,?,?,?,?,0,?,?)
                """, source.datasourceId(), source.datasourceName(), source.sourceSystemId(), source.sourceSystemName(), source.contractVersion(), source.enabled(), now, now);
    }

    private void upsertClient(String datasourceId, String clientId, long now) {
        int changed = jdbc.update("update ht_standalone_datasource_client set is_enable=1,is_del=0,updated_at=? where datasource_id=? and client_id=?", now, datasourceId, clientId);
        if (changed == 0) jdbc.update("""
                insert into ht_standalone_datasource_client(datasource_id,client_id,is_enable,is_del,created_at,updated_at)
                values (?,?,1,0,?,?)
                """, datasourceId, clientId, now, now);
    }

    private void upsertTable(String datasourceId, TableDefinition table, long now) {
        int changed = jdbc.update("""
                update ht_standalone_table set table_id=?,table_comment=?,schema_version=?,is_enable=?,is_del=0,updated_at=?
                 where datasource_id=? and table_name=?
                """, table.tableId(), table.tableComment(), table.schemaVersion(), table.enabled(), now, datasourceId, table.tableName());
        if (changed == 0) jdbc.update("""
                insert into ht_standalone_table(datasource_id,table_name,table_id,table_comment,schema_version,is_enable,is_del,created_at,updated_at)
                values (?,?,?,?,?,?,0,?,?)
                """, datasourceId, table.tableName(), table.tableId(), table.tableComment(), table.schemaVersion(), table.enabled(), now, now);
    }

    private void upsertColumn(String datasourceId, String tableName, ColumnDefinition column, long now) {
        int changed = jdbc.update("""
                update ht_standalone_table_column set column_comment=?,data_type=?,ordinal_no=?,is_required=?,is_enable=?,is_del=0,updated_at=?
                 where datasource_id=? and table_name=? and column_name=?
                """, column.columnComment(), column.dataType(), column.ordinal(), column.required(), column.enabled(), now, datasourceId, tableName, column.columnName());
        if (changed == 0) jdbc.update("""
                insert into ht_standalone_table_column(datasource_id,table_name,column_name,column_comment,data_type,ordinal_no,is_required,is_enable,is_del,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,0,?,?)
                """, datasourceId, tableName, column.columnName(), column.columnComment(), column.dataType(), column.ordinal(), column.required(), column.enabled(), now, now);
    }

    private Definition definition(byte[] input) {
        Map<String, Object> root = json.object(input);
        return definition(object(root.get("datasource"), "datasource 必须是 JSON 对象"));
    }

    private Definition definition(Map<String, Object> source) {
        String datasourceId = identifier(text(source.get("datasourceId")), "datasource.datasourceId", 64);
        String name = text(source.get("datasourceName"));
        if (name.isBlank() || name.length() > 255) throw RelayException.invalid("datasource.datasourceName 必须为 1 到 255 字符");
        List<String> clients = identifiers(array(source.get("clientIds"), "datasource.clientIds 必须是 JSON 数组"), "datasource.clientIds", 128);
        List<Object> rawTables = array(source.get("tables"), "datasource.tables 必须是 JSON 数组");
        if (rawTables.isEmpty()) throw RelayException.invalid("datasource.tables 至少需要一张表");
        List<TableDefinition> tables = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (Object rawTable : rawTables) {
            Map<String, Object> table = object(rawTable, "datasource.tables 每项必须是 JSON 对象");
            String tableName = identifier(text(table.get("tableName")), "tableName", 200);
            if (!names.add(tableName)) throw RelayException.invalid("datasource.tables 中 tableName 不得重复");
            List<Object> rawColumns = array(table.get("columns"), "columns 必须是 JSON 数组");
            if (rawColumns.isEmpty()) throw RelayException.invalid("每张表至少需要一个 columns 字段定义");
            List<ColumnDefinition> columns = new ArrayList<>();
            Set<String> columnNames = new LinkedHashSet<>();
            int defaultOrdinal = 1;
            for (Object rawColumn : rawColumns) {
                Map<String, Object> column = object(rawColumn, "columns 每项必须是 JSON 对象");
                String columnName = identifier(text(column.get("columnName")), "columnName", 200);
                if (!columnNames.add(columnName)) throw RelayException.invalid("同一张表的 columnName 不得重复");
                int ordinal = number(column.get("ordinal"), defaultOrdinal++);
                if (ordinal < 1 || ordinal > 10000) throw RelayException.invalid("column.ordinal 必须在 1 到 10000 之间");
                columns.add(new ColumnDefinition(columnName, textLimit(column.get("columnComment"), 255), textLimit(column.get("dataType"), 100), ordinal,
                        flag(column.get("required"), false), flag(column.get("enabled"), true)));
            }
            tables.add(new TableDefinition(textLimit(table.get("tableId"), 128), tableName, textLimit(table.get("tableComment"), 255),
                    textLimit(table.get("schemaVersion"), 100), flag(table.get("enabled"), true), List.copyOf(columns)));
        }
        return new Definition(datasourceId, name, textLimit(source.get("sourceSystemId"), 128), textLimit(source.get("sourceSystemName"), 255),
                textLimit(source.get("contractVersion"), 100), flag(source.get("enabled"), true), List.copyOf(clients), List.copyOf(tables));
    }

    private static Map<String, Object> object(Object value, String message) {
        if (!(value instanceof Map<?, ?> raw)) throw RelayException.invalid(message);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
        return result;
    }
    private static List<Object> array(Object value, String message) {
        if (!(value instanceof List<?> list)) throw RelayException.invalid(message);
        return new ArrayList<>(list);
    }
    private static List<String> identifiers(List<Object> values, String label, int max) {
        List<String> result = new ArrayList<>(); Set<String> seen = new LinkedHashSet<>();
        for (Object value : values) { String id = identifier(text(value), label, max); if (!seen.add(id)) throw RelayException.invalid(label + " 不得重复"); result.add(id); }
        return List.copyOf(result);
    }
    private static String identifier(String value, String label, int max) {
        if (value.length() < 1 || value.length() > max || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
            throw RelayException.invalid(label + " 仅允许字母、数字、下划线和连字符");
        return value;
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String textLimit(Object value, int max) { String text = text(value); if (text.length() > max) throw RelayException.invalid("配置字段长度超过限制"); return text; }
    private static boolean flag(Object value, boolean defaultValue) { return value == null ? defaultValue : value instanceof Boolean b ? b : invalidFlag(); }
    private static boolean invalidFlag() { throw RelayException.invalid("enabled 和 required 必须是布尔值"); }
    private static int number(Object value, int defaultValue) { return value == null ? defaultValue : value instanceof Number n ? n.intValue() : invalidNumber(); }
    private static int invalidNumber() { throw RelayException.invalid("column.ordinal 必须是整数"); }

    public record Datasource(String datasourceId, String datasourceName, boolean enabled, boolean deleted) { }
    public record Table(String datasourceId, String tableName, String schemaVersion, boolean enabled, boolean deleted) { }
    public record Column(String columnName, boolean required) { }
    public record SyncResult(String datasourceId, boolean duplicate, int tableCount, int columnCount) { }
    public record BatchSyncResult(List<String> datasourceIds, boolean duplicate, int tableCount, int columnCount) { }
    private record SyncAudit(String payloadHash, String datasourceId) { }
    private record Definition(String datasourceId, String datasourceName, String sourceSystemId, String sourceSystemName,
                              String contractVersion, boolean enabled, List<String> clientIds, List<TableDefinition> tables) { }
    private record TableDefinition(String tableId, String tableName, String tableComment, String schemaVersion,
                                   boolean enabled, List<ColumnDefinition> columns) { }
    private record ColumnDefinition(String columnName, String columnComment, String dataType, int ordinal,
                                    boolean required, boolean enabled) { }
}
